package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.api.PageResult;
import com.company.platform.common.core.id.SnowflakeIdGenerator;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.client.ApprovalClient;
import com.company.platform.order.entity.BizDevice;
import com.company.platform.order.entity.BizDeviceQueue;
import com.company.platform.order.entity.BizQueueJumpRequest;
import com.company.platform.order.entity.BizWorkOrder;
import com.company.platform.order.mapper.BizDeviceMapper;
import com.company.platform.order.mapper.BizDeviceQueueMapper;
import com.company.platform.order.mapper.BizQueueJumpRequestMapper;
import com.company.platform.order.mapper.BizWorkOrderMapper;
import com.company.platform.order.web.JumpRequestView;
import com.company.platform.order.web.QueueJumpApprovalCommand;
import com.company.platform.order.web.QueueJumpCreateCommand;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 设备队列插队申请与审批服务。
 *
 * <p>V1.4 明确：设备队列默认 FIFO；急单插队必须“销售申请 → 工厂管理审批”，
 * 审批通过后调整队列顺序并记录原因，被插队订单自动顺延并通过站内消息通知
 * 业务员，不做经济补偿。</p>
 *
 * <p><strong>审批中心：</strong>插队申请创建后会把审批单提交给审批中心，由厂长在
 * 统一待办中处理。审批中心在“通过”时回调本服务的
 * {@link #applyApproved(long, QueueJumpApprovalCommand)} 执行队列重排；驳回与撤销
 * 分别回调 {@link #markRejected(long, QueueJumpApprovalCommand)} 与
 * {@link #markCancelled(long)} 同步申请状态。审批中心不会在提交审批时回调本服务，
 * 因此不存在嵌套调用。</p>
 *
 * <p><strong>不可抢占生产中工单：</strong>设备当前生产工单始终保留在队首，
 * 插队只调整等待中的工单。申请位置早于“生产中工单数 + 1”时，审批会自动
 * 排到第一个可插位置之后，不会中断正在生产的订单。</p>
 */
@Service
public class QueueJumpService {

    /** 允许的申请状态。 */
    private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED");

    private final BizQueueJumpRequestMapper requestMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizDeviceMapper deviceMapper;
    private final BizDeviceQueueMapper queueMapper;
    private final WorkOrderService workOrderService;
    private final StationMessageService stationMessageService;
    private final ApprovalClient approvalClient;
    private final SnowflakeIdGenerator idGenerator;

    public QueueJumpService(BizQueueJumpRequestMapper requestMapper,
                            BizWorkOrderMapper workOrderMapper,
                            BizDeviceMapper deviceMapper,
                            BizDeviceQueueMapper queueMapper,
                            WorkOrderService workOrderService,
                            StationMessageService stationMessageService,
                            ApprovalClient approvalClient,
                            @Value("${app.snowflake.worker-id:1}") long workerId,
                            @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        this.requestMapper = requestMapper;
        this.workOrderMapper = workOrderMapper;
        this.deviceMapper = deviceMapper;
        this.queueMapper = queueMapper;
        this.workOrderService = workOrderService;
        this.stationMessageService = stationMessageService;
        this.approvalClient = approvalClient;
        this.idGenerator = new SnowflakeIdGenerator(workerId, datacenterId);
    }

    /**
     * 销售发起插队申请。
     *
     * <p>只有工单所属业务员可以申请（管理员用于联调/代申请）。工单必须处于
     * 排队中且已在设备队列里；目标位置必须早于当前位置，并且不能抢占正在
     * 生产的工单。同一工单同时只允许一条待审批申请。</p>
     *
     * <p>申请落库后同步把审批单提交给审批中心；提交失败时整个事务回滚，不会
     * 留下“有人申请但没人审批”的单据。</p>
     */
    @Transactional
    public JumpRequestView create(long workOrderId, QueueJumpCreateCommand command, Long userId) {
        if (userId == null) {
            throw new BizException(403, "机器令牌没有业务员身份，不能发起插队申请");
        }
        BizWorkOrder workOrder = workOrderMapper.selectOneById(workOrderId);
        if (workOrder == null) {
            throw new BizException(404, "工单不存在：" + workOrderId);
        }
        if (!SecurityUtils.isServiceAccount() && !SecurityUtils.hasAnyRole("ADMIN")
                && !Objects.equals(workOrder.getSalesUserId(), userId)) {
            throw new BizException(403, "只有工单所属业务员可以申请插队");
        }
        if (!"QUEUED".equals(workOrder.getStatus()) || workOrder.getDeviceId() == null) {
            throw new BizException(409, "只有排队中的工单可以申请插队");
        }
        BizDevice device = deviceMapper.selectOneById(workOrder.getDeviceId());
        if (device == null) {
            throw new BizException(404, "工单设备不存在");
        }
        List<BizDeviceQueue> active = queueMapper.selectActiveByDevice(device.getId());
        int currentPosition = positionOf(active, workOrder.getId());
        if (currentPosition <= 0) {
            throw new BizException(409, "工单不在活跃队列中，无法申请插队");
        }
        int runningPrefix = runningPrefix(active);
        int earliest = runningPrefix + 1;
        if (command.targetPosition() < earliest) {
            throw new BizException(400, "设备正在生产，最早只能排到第 " + earliest + " 位");
        }
        if (command.targetPosition() >= currentPosition) {
            throw new BizException(400, "目标位置必须早于当前第 " + currentPosition + " 位");
        }
        if (requestMapper.countPendingByWorkOrder(workOrderId) > 0) {
            throw new BizException(409, "该工单已有待审批的插队申请");
        }
        LocalDateTime now = LocalDateTime.now();
        BizQueueJumpRequest request = new BizQueueJumpRequest();
        request.setRequestNo("QJ-" + idGenerator.nextId());
        request.setWorkOrderId(workOrderId);
        request.setDeviceId(device.getId());
        request.setFactoryId(workOrder.getFactoryId());
        request.setRequestedBy(userId);
        request.setTargetPosition(command.targetPosition());
        request.setReason(command.reason().trim());
        request.setStatus("PENDING");
        request.setDisplacedCount(0);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        requestMapper.insert(request);
        // 提交审批中心：申请与审批单一一对应，审批通过后由审批中心回调执行重排。
        approvalClient.submitQueueJump(request.getRequestNo(), request.getId(), workOrder.getId(),
                workOrder.getWorkOrderNo(), device.getDeviceNo(), request.getTargetPosition(),
                request.getReason());
        return buildView(request);
    }

    /**
     * 插队申请分页。
     *
     * <p>销售只看自己发起的申请，厂长看本厂，管理员/机器令牌看全部。状态
     * 筛选支持 PENDING/APPROVED/REJECTED/CANCELLED。</p>
     */
    @Transactional(readOnly = true)
    public PageResult<JumpRequestView> page(long pageNumber, long pageSize, String status, Long factoryId) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(normalized)) {
                throw new BizException(400, "非法申请状态：" + status);
            }
            conditions.add("status = ?");
            arguments.add(normalized);
        }
        if (factoryId != null) {
            conditions.add("factory_id = ?");
            arguments.add(factoryId);
        }
        appendScope(conditions, arguments);
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("created_at", false).orderBy("id", false);
        Page<BizQueueJumpRequest> page = requestMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<JumpRequestView> records = new ArrayList<>(page.getRecords().size());
        for (BizQueueJumpRequest request : page.getRecords()) {
            records.add(buildView(request));
        }
        return new PageResult<>(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize());
    }

    /** 申请详情；超出数据范围时抛 403。 */
    @Transactional(readOnly = true)
    public JumpRequestView get(long id) {
        BizQueueJumpRequest request = requestMapper.selectOneById(id);
        if (request == null) {
            throw new BizException(404, "插队申请不存在：" + id);
        }
        requireViewAccess(request);
        return buildView(request);
    }

    /**
     * 审批通过并重排队列（由审批中心在审批通过后回调）。
     *
     * <p>事务步骤：锁定申请 → 校验审批权限 → 锁定工单和设备 → 锁定活跃队列
     * → 按目标位置重排 → 更新序号 → 写插队快照 → 重算预计时间 → 给被顺延
     * 业务员和申请人发站内消息。任一步失败整体回滚，不会出现“申请已通过但
     * 队列未调整”的中间状态。</p>
     */
    @Transactional
    public JumpRequestView applyApproved(long id, QueueJumpApprovalCommand command) {
        BizQueueJumpRequest request = requireForUpdate(id);
        requireApproveAccess(request);
        if (!"PENDING".equals(request.getStatus())) {
            throw new BizException(409, "只有待审批申请可以通过");
        }
        BizWorkOrder workOrder = workOrderMapper.selectForUpdate(request.getWorkOrderId());
        if (workOrder == null || !"QUEUED".equals(workOrder.getStatus())) {
            throw new BizException(409, "工单已不在排队状态，申请失效");
        }
        if (!Objects.equals(workOrder.getDeviceId(), request.getDeviceId())) {
            throw new BizException(409, "工单设备已变化，申请失效");
        }
        BizDevice device = deviceMapper.selectForUpdate(request.getDeviceId());
        if (device == null) {
            throw new BizException(404, "申请目标设备不存在");
        }
        List<BizDeviceQueue> active = queueMapper.selectActiveForUpdate(device.getId());
        BizDeviceQueue ownQueue = findQueue(active, workOrder.getId());
        if (ownQueue == null || !"WAITING".equals(ownQueue.getStatus())) {
            throw new BizException(409, "工单已不在等待队列，申请失效");
        }
        int currentPosition = positionOf(active, workOrder.getId());
        int runningPrefix = runningPrefix(active);
        List<BizDeviceQueue> waiting = new ArrayList<>();
        for (BizDeviceQueue queue : active) {
            if ("WAITING".equals(queue.getStatus()) && !queue.getId().equals(ownQueue.getId())) {
                waiting.add(queue);
            }
        }
        if (waiting.isEmpty()) {
            throw new BizException(409, "设备队列已变化，当前没有可顺延的工单");
        }
        // 目标位置是绝对位置，减去生产中前缀和队首偏移，得到等待列表插入点。
        int insertIndex = request.getTargetPosition() - runningPrefix - 1;
        insertIndex = Math.max(0, Math.min(insertIndex, waiting.size() - 1));
        List<BizDeviceQueue> displaced = new ArrayList<>(waiting.subList(insertIndex, waiting.size()));
        if (displaced.isEmpty()) {
            throw new BizException(409, "队列已变化，本次申请不再产生插队效果，请驳回后重新申请");
        }

        // 生产中工单保持前缀，等待工单按新顺序重排。
        List<BizDeviceQueue> newOrder = new ArrayList<>(active.size());
        for (BizDeviceQueue queue : active) {
            if ("RUNNING".equals(queue.getStatus())) {
                newOrder.add(queue);
            }
        }
        for (int i = 0; i < insertIndex; i++) {
            newOrder.add(waiting.get(i));
        }
        newOrder.add(ownQueue);
        for (int i = insertIndex; i < waiting.size(); i++) {
            newOrder.add(waiting.get(i));
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, Long> oldSeq = new HashMap<>();
        for (BizDeviceQueue queue : active) {
            oldSeq.put(queue.getId(), queue.getQueueSeq());
        }
        for (int i = 0; i < newOrder.size(); i++) {
            queueMapper.updateSeq(newOrder.get(i).getId(), (long) (i + 1) * 1000L, now);
        }
        Long originalSeq = oldSeq.get(ownQueue.getId());
        queueMapper.markJump(ownQueue.getId(), originalSeq, request.getId(), request.getReason(), now);

        request.setStatus("APPROVED");
        request.setApprovedBy(SecurityUtils.userId());
        request.setApprovedAt(now);
        request.setApprovalRemark(trimToNull(command == null ? null : command.remark()));
        request.setDisplacedCount(displaced.size());
        request.setUpdatedAt(now);
        requestMapper.update(request);

        // 序号必须先落库，再重算预计时间；replan 会读取新的 queue_seq。
        workOrderService.replanDeviceQueue(device);
        notifyDisplaced(device, workOrder, request, waiting, insertIndex, runningPrefix);
        notifyRequester(request, workOrder, device, true);
        return buildView(request);
    }

    /**
     * 驳回插队申请（由审批中心在驳回后回调）。
     *
     * <p>驳回只更新申请状态并通知申请人，不修改队列。</p>
     */
    @Transactional
    public JumpRequestView markRejected(long id, QueueJumpApprovalCommand command) {
        BizQueueJumpRequest request = requireForUpdate(id);
        requireApproveAccess(request);
        if (!"PENDING".equals(request.getStatus())) {
            throw new BizException(409, "只有待审批申请可以驳回");
        }
        String remark = command == null ? null : trimToNull(command.remark());
        if (remark == null) {
            throw new BizException(400, "驳回时必须填写审批意见");
        }
        LocalDateTime now = LocalDateTime.now();
        request.setStatus("REJECTED");
        request.setApprovedBy(SecurityUtils.userId());
        request.setApprovedAt(now);
        request.setApprovalRemark(remark);
        request.setUpdatedAt(now);
        requestMapper.update(request);
        BizWorkOrder workOrder = workOrderMapper.selectOneById(request.getWorkOrderId());
        BizDevice device = deviceMapper.selectOneById(request.getDeviceId());
        notifyRequester(request, workOrder, device, false);
        return buildView(request);
    }

    /**
     * 申请人撤销尚未审批的申请（由审批中心在撤销后回调）。
     *
     * <p>撤销人必须是申请人本人；管理员与机器令牌可代为撤销。</p>
     */
    @Transactional
    public JumpRequestView markCancelled(long id) {
        BizQueueJumpRequest request = requireForUpdate(id);
        Long userId = SecurityUtils.userId();
        if (!Objects.equals(request.getRequestedBy(), userId)
                && !SecurityUtils.hasAnyRole("ADMIN") && !SecurityUtils.isServiceAccount()) {
            throw new BizException(403, "只有申请人可以撤销插队申请");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BizException(409, "只有待审批申请可以撤销");
        }
        request.setStatus("CANCELLED");
        request.setUpdatedAt(LocalDateTime.now());
        requestMapper.update(request);
        return buildView(request);
    }

    /** 读取申请并加锁。 */
    @Transactional
    public BizQueueJumpRequest requireForUpdate(long id) {
        BizQueueJumpRequest request = requestMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(404, "插队申请不存在：" + id);
        }
        return request;
    }

    /** 构建申请视图，实时计算工单当前队列位置。 */
    private JumpRequestView buildView(BizQueueJumpRequest request) {
        BizWorkOrder workOrder = workOrderMapper.selectOneById(request.getWorkOrderId());
        BizDevice device = deviceMapper.selectOneById(request.getDeviceId());
        Integer currentPosition = null;
        if (workOrder != null && workOrder.getDeviceId() != null) {
            List<BizDeviceQueue> active = queueMapper.selectActiveByDevice(workOrder.getDeviceId());
            int position = positionOf(active, workOrder.getId());
            currentPosition = position <= 0 ? null : position;
        }
        return JumpRequestView.from(request, workOrder, device, currentPosition);
    }

    /**
     * 通知被顺延工单的业务员。
     *
     * <p>只在审批通过时发送；内容包含插队工单、原因和新位置。业务员为空
     * （历史订单没有归属）时跳过，避免产生无接收人的消息。</p>
     */
    private void notifyDisplaced(BizDevice device, BizWorkOrder jumper, BizQueueJumpRequest request,
                                 List<BizDeviceQueue> waiting, int insertIndex, int runningPrefix) {
        for (int i = insertIndex; i < waiting.size(); i++) {
            BizDeviceQueue queue = waiting.get(i);
            BizWorkOrder displaced = workOrderMapper.selectOneById(queue.getWorkOrderId());
            if (displaced == null || displaced.getSalesUserId() == null) {
                continue;
            }
            int newPosition = i + runningPrefix + 2;
            String content = "订单 " + displaced.getOrderNo() + " 的工单 " + displaced.getWorkOrderNo()
                    + " 因急单 " + jumper.getWorkOrderNo() + "（订单 " + jumper.getOrderNo()
                    + "）插队审批通过，顺延至设备 " + device.getDeviceNo() + " 队列第 "
                    + newPosition + " 位。插队原因：" + request.getReason();
            stationMessageService.send(displaced.getSalesUserId(), StationMessageService.TYPE_QUEUE_DISPLACED,
                    "订单排队被顺延通知", content, "WORK_ORDER", String.valueOf(displaced.getId()),
                    "/orders/" + displaced.getOrderNo());
        }
    }

    /** 给申请人发送审批结果消息。 */
    private void notifyRequester(BizQueueJumpRequest request, BizWorkOrder workOrder, BizDevice device,
                                 boolean approved) {
        if (request.getRequestedBy() == null) {
            return;
        }
        String action = approved ? "已通过" : "被驳回";
        String content = "插队申请 " + request.getRequestNo() + "（工单 "
                + (workOrder == null ? request.getWorkOrderId() : workOrder.getWorkOrderNo())
                + "，设备 " + (device == null ? request.getDeviceId() : device.getDeviceNo())
                + "）" + action + "。"
                + (request.getApprovalRemark() == null ? "" : "审批意见：" + request.getApprovalRemark());
        stationMessageService.send(request.getRequestedBy(), StationMessageService.TYPE_QUEUE_JUMP_RESULT,
                "插队申请" + action, content, "QUEUE_JUMP", String.valueOf(request.getId()),
                workOrder == null ? "/production/schedule" : "/orders/" + workOrder.getOrderNo());
    }

    /** 追加申请列表数据范围。 */
    private void appendScope(List<String> conditions, List<Object> arguments) {
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            Long factoryId = SecurityUtils.factoryId();
            conditions.add("factory_id = ?");
            arguments.add(factoryId == null ? -1L : factoryId);
            return;
        }
        Long userId = SecurityUtils.userId();
        conditions.add("requested_by = ?");
        arguments.add(userId == null ? -1L : userId);
    }

    /** 单条申请查看范围。 */
    private void requireViewAccess(BizQueueJumpRequest request) {
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN", "FINANCE")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            if (!Objects.equals(request.getFactoryId(), SecurityUtils.factoryId())) {
                throw new BizException(403, "无权查看其他工厂的插队申请");
            }
            return;
        }
        if (!Objects.equals(request.getRequestedBy(), SecurityUtils.userId())) {
            throw new BizException(403, "无权查看其他业务员的插队申请");
        }
    }

    /** 审批权限：管理员/机器令牌，或申请所属工厂厂长。 */
    private void requireApproveAccess(BizQueueJumpRequest request) {
        if (SecurityUtils.isServiceAccount() || SecurityUtils.hasAnyRole("ADMIN")) {
            return;
        }
        if (SecurityUtils.hasAnyRole("FACTORY_MANAGER")) {
            if (!Objects.equals(request.getFactoryId(), SecurityUtils.factoryId())) {
                throw new BizException(403, "只能审批本厂的插队申请");
            }
            return;
        }
        throw new BizException(403, "无插队审批权限");
    }

    /** 找指定工单的队列记录。 */
    private BizDeviceQueue findQueue(List<BizDeviceQueue> active, Long workOrderId) {
        for (BizDeviceQueue queue : active) {
            if (queue.getWorkOrderId().equals(workOrderId)) {
                return queue;
            }
        }
        return null;
    }

    /** 计算工单在活跃队列中的位置；不在队列返回 0。 */
    private int positionOf(List<BizDeviceQueue> active, Long workOrderId) {
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).getWorkOrderId().equals(workOrderId)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** 统计队首连续的生产中工单数量，插队不能越过这些工单。 */
    private int runningPrefix(List<BizDeviceQueue> active) {
        int count = 0;
        for (BizDeviceQueue queue : active) {
            if ("RUNNING".equals(queue.getStatus())) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /** 去首尾空格，空字符串转 null。 */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
