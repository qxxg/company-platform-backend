package com.company.platform.approval.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.id.SnowflakeIdGenerator;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.approval.entity.ApprovalDefinition;
import com.company.platform.approval.entity.ApprovalInstance;
import com.company.platform.approval.entity.ApprovalRecord;
import com.company.platform.approval.entity.ApprovalTask;
import com.company.platform.approval.mapper.ApprovalDefinitionMapper;
import com.company.platform.approval.mapper.ApprovalInstanceMapper;
import com.company.platform.approval.mapper.ApprovalRecordMapper;
import com.company.platform.approval.mapper.ApprovalTaskMapper;
import com.company.platform.approval.web.ApprovalPageView;
import com.company.platform.approval.web.ApprovalView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 审批中心核心服务。
 *
 * <p>流程：提交申请时按“业务类型 + 金额档位”匹配审批定义，生成审批实例与待办
 * 任务；审批人同意或驳回后实例结束，同意时由业务动作执行器落地实际变更。</p>
 *
 * <p>数据范围：申请人只能看自己的申请；审批人看与自己相关的待办；管理员与
 * 拥有 {@code approval:read} 权限的角色可以查看全部审批单。</p>
 */
@Service
public class ApprovalService {

    private final ApprovalDefinitionMapper definitionMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalTaskMapper taskMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalPayloadReader payloadReader;
    private final ObjectMapper objectMapper;
    private final Map<String, ApprovalActionHandler> handlers = new HashMap<>();
    private final SnowflakeIdGenerator idGenerator;

    public ApprovalService(ApprovalDefinitionMapper definitionMapper,
                           ApprovalInstanceMapper instanceMapper,
                           ApprovalTaskMapper taskMapper,
                           ApprovalRecordMapper recordMapper,
                           ApprovalPayloadReader payloadReader,
                           ObjectMapper objectMapper,
                           List<ApprovalActionHandler> handlerList,
                           @Value("${app.snowflake.worker-id:1}") long workerId,
                           @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.recordMapper = recordMapper;
        this.payloadReader = payloadReader;
        this.objectMapper = objectMapper;
        this.idGenerator = new SnowflakeIdGenerator(workerId, datacenterId);
        for (ApprovalActionHandler handler : handlerList) {
            this.handlers.put(handler.businessType(), handler);
        }
    }

    /**
     * 提交审批申请。
     *
     * <p>按业务类型与金额命中启用的审批定义（优先级数字小的先匹配）；没有匹配的
     * 定义时拒绝提交，避免出现无人审批的申请。</p>
     */
    @Transactional
    public ApprovalView submit(com.company.platform.approval.web.ApprovalSubmitCommand command) {
        String businessType = command.businessType().trim().toUpperCase(Locale.ROOT);
        Long amount = command.amount() == null ? 0L : command.amount();
        ApprovalDefinition definition = matchDefinition(businessType, amount);
        if (definition == null) {
            throw new BizException(409, "未配置业务类型「" + businessType + "」的审批流程，无法提交申请");
        }
        LocalDateTime now = LocalDateTime.now();
        ApprovalInstance instance = new ApprovalInstance();
        instance.setInstanceNo("AP-" + idGenerator.nextId());
        instance.setBusinessType(businessType);
        instance.setBusinessId(command.businessId().trim());
        instance.setTitle(command.title().trim());
        instance.setAmount(command.amount());
        instance.setFactoryId(command.factoryId());
        instance.setApplicantUserId(SecurityUtils.userId());
        instance.setApplicantName(SecurityUtils.nickname());
        instance.setStatus("PENDING");
        instance.setDefinitionId(definition.getId());
        instance.setCurrentStep(1);
        instance.setPayload(writePayload(command.payload()));
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        instance.setVersion(0);
        instanceMapper.insert(instance);

        ApprovalTask task = new ApprovalTask();
        task.setInstanceId(instance.getId());
        task.setStepNo(1);
        task.setApproverRole(definition.getApproverRole());
        task.setApproverUserId(definition.getApproverUserId());
        task.setStatus("PENDING");
        task.setCreatedAt(now);
        taskMapper.insert(task);

        appendRecord(instance.getId(), "SUBMIT", "提交审批申请", null);
        return buildView(instance);
    }

    /**
     * 审批单分页。
     *
     * @param pageNumber  页码
     * @param pageSize    每页条数
     * @param businessType 业务类型过滤
     * @param status      状态过滤
     * @param mine        只看本人提交的申请
     */
    @Transactional(readOnly = true)
    public ApprovalPageView page(long pageNumber, long pageSize, String businessType, String status,
                                  boolean mine) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (businessType != null && !businessType.isBlank()) {
            conditions.add("business_type = ?");
            arguments.add(businessType.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (mine) {
            conditions.add("applicant_user_id = ?");
            arguments.add(SecurityUtils.userId());
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("created_at", false).orderBy("id", false);
        Page<ApprovalInstance> page = instanceMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<ApprovalView> records = buildViews(page.getRecords());

        QueryWrapper summaryQuery = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            summaryQuery.where(String.join(" AND ", conditions), arguments.toArray());
        }
        List<ApprovalInstance> all = instanceMapper.selectListByQuery(summaryQuery);
        long pending = 0;
        long approved = 0;
        long rejected = 0;
        for (ApprovalInstance instance : all) {
            switch (instance.getStatus() == null ? "" : instance.getStatus()) {
                case "PENDING" -> pending++;
                case "APPROVED" -> approved++;
                case "REJECTED" -> rejected++;
                default -> {
                    // 已撤销不计入统计。
                }
            }
        }
        return new ApprovalPageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(),
                new ApprovalPageView.Summary(all.size(), pending, approved, rejected));
    }

    /**
     * 我的待办分页。
     *
     * <p>待办命中条件：任务指定本人，或任务的审批角色在当前用户角色列表中。</p>
     */
    @Transactional(readOnly = true)
    public ApprovalPageView todo(long pageNumber, long pageSize, String businessType) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        conditions.add("status = 'PENDING'");
        List<String> roles = SecurityUtils.roles();
        if (SecurityUtils.hasAnyRole("ADMIN")) {
            // 管理员是兜底审批人：待办里展示全部待审批任务。
        } else if (roles.isEmpty()) {
            conditions.add("approver_user_id = ?");
            arguments.add(SecurityUtils.userId() == null ? -1L : SecurityUtils.userId());
        } else {
            StringBuilder rolePlaceholders = new StringBuilder();
            for (int i = 0; i < roles.size(); i++) {
                rolePlaceholders.append(i == 0 ? "?" : ",?");
            }
            conditions.add("(approver_user_id = ? OR approver_role IN (" + rolePlaceholders + "))");
            arguments.add(SecurityUtils.userId() == null ? -1L : SecurityUtils.userId());
            arguments.addAll(roles);
        }
        // 业务类型过滤在审批单上做：先按类型取实例 ID，再过滤任务，避免跨表别名。
        if (businessType != null && !businessType.isBlank()) {
            List<Long> instanceIds = instanceMapper.selectListByQuery(QueryWrapper.create()
                            .select("id")
                            .where("business_type = ? AND status = 'PENDING'",
                                    businessType.trim().toUpperCase(Locale.ROOT)))
                    .stream().map(ApprovalInstance::getId).toList();
            if (instanceIds.isEmpty()) {
                return new ApprovalPageView(List.of(), 0, pageNumber, pageSize,
                        new ApprovalPageView.Summary(0, 0, 0, 0));
            }
            conditions.add("instance_id IN (" + placeholders(instanceIds.size()) + ")");
            arguments.addAll(instanceIds);
        }
        QueryWrapper query = QueryWrapper.create()
                .where(String.join(" AND ", conditions), arguments.toArray());
        Page<ApprovalTask> page = taskMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<ApprovalInstance> instances = loadInstances(page.getRecords().stream()
                .map(ApprovalTask::getInstanceId).distinct().toList());
        Map<Long, ApprovalInstance> instanceMap = new LinkedHashMap<>();
        for (ApprovalInstance instance : instances) {
            instanceMap.put(instance.getId(), instance);
        }
        List<ApprovalView> records = new ArrayList<>();
        for (ApprovalTask task : page.getRecords()) {
            ApprovalInstance instance = instanceMap.get(task.getInstanceId());
            if (instance != null) {
                records.add(buildView(instance));
            }
        }
        long total = page.getTotalRow();
        return new ApprovalPageView(records, total, page.getPageNumber(), page.getPageSize(),
                new ApprovalPageView.Summary(total, total, 0, 0));
    }

    /** 审批单详情，含任务与轨迹。 */
    @Transactional(readOnly = true)
    public ApprovalView get(long id) {
        ApprovalInstance instance = instanceMapper.selectOneById(id);
        if (instance == null) {
            throw new BizException(404, "审批单不存在：" + id);
        }
        requireReadable(instance);
        return buildView(instance);
    }

    /**
     * 审批同意。
     *
     * <p>业务动作在实例置为通过之前执行：业务落地失败会回滚整个事务，审批单
     * 保持待审批，不会出现“审批通过但业务未生效”的中间状态。</p>
     */
    @Transactional
    public ApprovalView approve(long taskId, String comment) {
        ApprovalTask task = taskMapper.selectForUpdate(taskId);
        if (task == null) {
            throw new BizException(404, "审批任务不存在：" + taskId);
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new BizException(409, "该审批任务已处理，当前状态：" + task.getStatus());
        }
        ApprovalInstance instance = instanceMapper.selectForUpdate(task.getInstanceId());
        if (instance == null) {
            throw new BizException(404, "审批单不存在：" + task.getInstanceId());
        }
        if (!"PENDING".equals(instance.getStatus())) {
            throw new BizException(409, "该审批单已结束，当前状态：" + instance.getStatus());
        }
        requireApprover(task);

        LocalDateTime now = LocalDateTime.now();
        ApprovalActionHandler handler = handlers.get(instance.getBusinessType());
        if (handler != null) {
            // 业务动作先执行：落地失败时审批事务整体回滚，实例保持待审批。
            handler.onApproved(instance, trimToNull(comment));
        }
        task.setStatus("APPROVED");
        task.setComment(trimToNull(comment));
        task.setActedBy(SecurityUtils.userId());
        task.setActedAt(now);
        taskMapper.update(task);

        instance.setStatus("APPROVED");
        instance.setResultNote(trimToNull(comment));
        instance.setFinishedAt(now);
        instance.setUpdatedAt(now);
        instanceMapper.update(instance);
        appendRecord(instance.getId(), "APPROVE", "审批通过", comment);
        return buildView(instance);
    }

    /** 审批驳回；必须填写意见。 */
    @Transactional
    public ApprovalView reject(long taskId, String comment) {
        String reason = trimToNull(comment);
        if (reason == null) {
            throw new BizException(400, "驳回时必须填写审批意见");
        }
        ApprovalTask task = taskMapper.selectForUpdate(taskId);
        if (task == null) {
            throw new BizException(404, "审批任务不存在：" + taskId);
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new BizException(409, "该审批任务已处理，当前状态：" + task.getStatus());
        }
        ApprovalInstance instance = instanceMapper.selectForUpdate(task.getInstanceId());
        if (instance == null) {
            throw new BizException(404, "审批单不存在：" + task.getInstanceId());
        }
        if (!"PENDING".equals(instance.getStatus())) {
            throw new BizException(409, "该审批单已结束，当前状态：" + instance.getStatus());
        }
        requireApprover(task);

        LocalDateTime now = LocalDateTime.now();
        // 业务同步先执行：失败时审批事务回滚，实例保持待审批。
        ApprovalActionHandler handler = handlers.get(instance.getBusinessType());
        if (handler != null) {
            handler.onRejected(instance, reason);
        }
        task.setStatus("REJECTED");
        task.setComment(reason);
        task.setActedBy(SecurityUtils.userId());
        task.setActedAt(now);
        taskMapper.update(task);

        instance.setStatus("REJECTED");
        instance.setResultNote(reason);
        instance.setFinishedAt(now);
        instance.setUpdatedAt(now);
        instanceMapper.update(instance);
        appendRecord(instance.getId(), "REJECT", "审批驳回", reason);
        return buildView(instance);
    }

    /** 申请人撤销待审批申请。 */
    @Transactional
    public ApprovalView cancel(long instanceId) {
        ApprovalInstance instance = instanceMapper.selectForUpdate(instanceId);
        if (instance == null) {
            throw new BizException(404, "审批单不存在：" + instanceId);
        }
        if (!"PENDING".equals(instance.getStatus())) {
            throw new BizException(409, "只有待审批的申请可以撤销，当前状态：" + instance.getStatus());
        }
        Long applicantId = instance.getApplicantUserId();
        Long currentUserId = SecurityUtils.userId();
        if (!java.util.Objects.equals(applicantId, currentUserId)
                && !SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            throw new BizException(403, "只有申请人本人可以撤销该申请");
        }
        LocalDateTime now = LocalDateTime.now();
        // 业务同步先执行：失败时审批事务回滚，实例保持待审批。
        ApprovalActionHandler handler = handlers.get(instance.getBusinessType());
        if (handler != null) {
            handler.onCancelled(instance);
        }
        List<ApprovalTask> tasks = taskMapper.selectListByQuery(QueryWrapper.create()
                .where("instance_id = ? AND status = 'PENDING'", instanceId));
        for (ApprovalTask task : tasks) {
            task.setStatus("SKIPPED");
            task.setActedBy(currentUserId);
            task.setActedAt(now);
            taskMapper.update(task);
        }
        instance.setStatus("CANCELLED");
        instance.setFinishedAt(now);
        instance.setUpdatedAt(now);
        instanceMapper.update(instance);
        appendRecord(instanceId, "CANCEL", "申请人撤销", null);
        return buildView(instance);
    }

    /** 匹配审批定义：业务类型 + 金额区间 + 优先级。 */
    private ApprovalDefinition matchDefinition(String businessType, Long amount) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        conditions.add("business_type = ?");
        arguments.add(businessType);
        conditions.add("status = 'ACTIVE'");
        conditions.add("amount_min <= ?");
        arguments.add(amount);
        conditions.add("(amount_max IS NULL OR amount_max > ?)");
        arguments.add(amount);
        List<ApprovalDefinition> definitions = definitionMapper.selectListByQuery(QueryWrapper.create()
                .where(String.join(" AND ", conditions), arguments.toArray())
                .orderBy("priority", true)
                .orderBy("id", true)
                .limit(1));
        return definitions.isEmpty() ? null : definitions.get(0);
    }

    /** 校验当前用户是否为该任务的审批人。 */
    private void requireApprover(ApprovalTask task) {
        Long currentUserId = SecurityUtils.userId();
        if (task.getApproverUserId() != null) {
            if (!java.util.Objects.equals(task.getApproverUserId(), currentUserId)
                    && !SecurityUtils.hasAnyRole("ADMIN")) {
                throw new BizException(403, "该审批任务指派给其他审批人");
            }
            return;
        }
        if (task.getApproverRole() != null && SecurityUtils.hasAnyRole(task.getApproverRole())) {
            return;
        }
        if (SecurityUtils.hasAnyRole("ADMIN")) {
            return;
        }
        throw new BizException(403, "没有该审批任务的处理权限");
    }

    /** 校验当前用户能否查看该审批单。 */
    private void requireReadable(ApprovalInstance instance) {
        if (SecurityUtils.hasAnyRole("ADMIN") || SecurityUtils.hasPermission("approval:read")) {
            return;
        }
        if (java.util.Objects.equals(instance.getApplicantUserId(), SecurityUtils.userId())) {
            return;
        }
        throw new BizException(403, "无权查看该审批单");
    }

    /** 批量构建审批单视图。 */
    private List<ApprovalView> buildViews(List<ApprovalInstance> instances) {
        if (instances.isEmpty()) {
            return List.of();
        }
        List<Long> ids = instances.stream().map(ApprovalInstance::getId).toList();
        Map<Long, List<ApprovalTask>> tasksByInstance = new LinkedHashMap<>();
        for (ApprovalTask task : taskMapper.selectListByQuery(QueryWrapper.create()
                .where("instance_id IN (" + placeholders(ids.size()) + ")", ids.toArray())
                .orderBy("step_no", true))) {
            tasksByInstance.computeIfAbsent(task.getInstanceId(), key -> new ArrayList<>()).add(task);
        }
        Map<Long, List<ApprovalRecord>> recordsByInstance = new LinkedHashMap<>();
        for (ApprovalRecord record : recordMapper.selectListByQuery(QueryWrapper.create()
                .where("instance_id IN (" + placeholders(ids.size()) + ")", ids.toArray())
                .orderBy("id", true))) {
            recordsByInstance.computeIfAbsent(record.getInstanceId(), key -> new ArrayList<>()).add(record);
        }
        Map<Long, ApprovalDefinition> definitions = new LinkedHashMap<>();
        List<Long> definitionIds = instances.stream()
                .map(ApprovalInstance::getDefinitionId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!definitionIds.isEmpty()) {
            for (ApprovalDefinition definition : definitionMapper.selectListByQuery(QueryWrapper.create()
                    .where("id IN (" + placeholders(definitionIds.size()) + ")", definitionIds.toArray()))) {
                definitions.put(definition.getId(), definition);
            }
        }
        List<ApprovalView> views = new ArrayList<>(instances.size());
        for (ApprovalInstance instance : instances) {
            List<ApprovalView.Task> taskViews = new ArrayList<>();
            for (ApprovalTask task : tasksByInstance.getOrDefault(instance.getId(), List.of())) {
                taskViews.add(ApprovalView.Task.from(task));
            }
            List<ApprovalView.Record> recordViews = new ArrayList<>();
            for (ApprovalRecord record : recordsByInstance.getOrDefault(instance.getId(), List.of())) {
                recordViews.add(new ApprovalView.Record(record.getId(), record.getAction(),
                        record.getOperatorUserId(), record.getOperatorName(), record.getComment(),
                        record.getCreatedAt()));
            }
            ApprovalDefinition definition = definitions.get(instance.getDefinitionId());
            views.add(ApprovalView.from(instance, definition == null ? null : definition.getName(),
                    taskViews, recordViews));
        }
        return views;
    }

    /** 构建单个审批单视图。 */
    private ApprovalView buildView(ApprovalInstance instance) {
        return buildViews(List.of(instance)).get(0);
    }

    /** 批量加载审批实例。 */
    private List<ApprovalInstance> loadInstances(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return instanceMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(ids.size()) + ")", ids.toArray()));
    }

    /** 追加审批轨迹。 */
    private void appendRecord(long instanceId, String action, String comment, String extra) {
        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instanceId);
        record.setAction(action);
        record.setOperatorUserId(SecurityUtils.userId());
        record.setOperatorName(SecurityUtils.nickname());
        record.setComment(extra == null ? comment : (comment == null ? extra : comment + "：" + extra));
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
    }

    /** 业务参数快照序列化。 */
    private String writePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BizException(400, "业务参数无法序列化");
        }
    }

    /** 解析业务参数快照。 */
    public Map<String, Object> readPayload(ApprovalInstance instance) {
        return payloadReader.read(instance == null ? null : instance.getPayload());
    }

    /** 供业务动作执行器使用的业务类型集合。 */
    public static Set<String> supportedBusinessTypes() {
        return Set.of("CREDIT_LIMIT", "INVOICE_VOID", "EXPENSE", "LOAN", "QUEUE_JUMP", "WAREHOUSE");
    }

    /** 去首尾空格，空字符串转 null。 */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 生成 IN 子句占位符。 */
    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }
}
