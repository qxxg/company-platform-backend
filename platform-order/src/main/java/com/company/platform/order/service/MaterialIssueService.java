package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.id.SnowflakeIdGenerator;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.entity.BizMaterial;
import com.company.platform.order.entity.BizMaterialIssue;
import com.company.platform.order.entity.BizMaterialIssueLine;
import com.company.platform.order.entity.BizWarehouse;
import com.company.platform.order.entity.BizWorkOrder;
import com.company.platform.order.mapper.BizInventoryMapper;
import com.company.platform.order.mapper.BizMaterialIssueLineMapper;
import com.company.platform.order.mapper.BizMaterialIssueMapper;
import com.company.platform.order.mapper.BizMaterialMapper;
import com.company.platform.order.mapper.BizWarehouseMapper;
import com.company.platform.order.mapper.BizWorkOrderMapper;
import com.company.platform.order.web.MaterialIssueCreateCommand;
import com.company.platform.order.web.MaterialIssuePageView;
import com.company.platform.order.web.MaterialIssueView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 工单领料服务。
 *
 * <p><strong>材料成本闭环：</strong>领料把仓库物料与生产工单关联起来，确认发料时
 * 扣减库存并把金额计入工单材料成本；工单材料成本由已发料领料单的明细实时汇总，
 * 不单独落库，避免两处维护。</p>
 *
 * <p><strong>库存与金额口径：</strong>单价、损耗率取自物料档案快照，金额 =
 * 数量 × 单价并按分截断，与采购入库完全一致；发料用条件更新扣减库存，库存不足
 * 时整单失败，不会出现部分扣减。</p>
 *
 * <p><strong>业务约束：</strong>领料仓库必须与工单同厂；工单必须已分配设备且未
 * 完成（排队中或生产中）；待发料单据可以取消，已发料单据保留历史。</p>
 */
@Service
public class MaterialIssueService {

    /** 工单允许领料的状态。 */
    private static final Set<String> ISSUEABLE_WORK_ORDER_STATUSES = Set.of("QUEUED", "RUNNING");

    private final BizMaterialIssueMapper issueMapper;
    private final BizMaterialIssueLineMapper lineMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizWarehouseMapper warehouseMapper;
    private final BizMaterialMapper materialMapper;
    private final BizInventoryMapper inventoryMapper;
    private final WarehouseService warehouseService;
    private final SnowflakeIdGenerator idGenerator;

    public MaterialIssueService(BizMaterialIssueMapper issueMapper,
                                BizMaterialIssueLineMapper lineMapper,
                                BizWorkOrderMapper workOrderMapper,
                                BizWarehouseMapper warehouseMapper,
                                BizMaterialMapper materialMapper,
                                BizInventoryMapper inventoryMapper,
                                WarehouseService warehouseService,
                                @Value("${app.snowflake.worker-id:1}") long workerId,
                                @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        this.issueMapper = issueMapper;
        this.lineMapper = lineMapper;
        this.workOrderMapper = workOrderMapper;
        this.warehouseMapper = warehouseMapper;
        this.materialMapper = materialMapper;
        this.inventoryMapper = inventoryMapper;
        this.warehouseService = warehouseService;
        this.idGenerator = new SnowflakeIdGenerator(workerId, datacenterId);
    }

    /**
     * 领料单分页，附带汇总指标。
     *
     * @param pageNumber  页码
     * @param pageSize    每页条数
     * @param keyword     单号/工单号/订单号关键字
     * @param workOrderId 工单过滤
     * @param warehouseId 仓库过滤
     * @param status      状态过滤
     */
    @Transactional(readOnly = true)
    public MaterialIssuePageView page(long pageNumber, long pageSize, String keyword, Long workOrderId,
                                      Long warehouseId, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(issue_no LIKE ? OR work_order_no LIKE ? OR order_no LIKE ?)");
            arguments.add(like);
            arguments.add(like);
            arguments.add(like);
        }
        if (workOrderId != null) {
            conditions.add("work_order_id = ?");
            arguments.add(workOrderId);
        }
        if (warehouseId != null) {
            conditions.add("warehouse_id = ?");
            arguments.add(warehouseId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("created_at", false).orderBy("id", false);
        Page<BizMaterialIssue> page = issueMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<MaterialIssueView> records = buildViews(page.getRecords());

        QueryWrapper summaryQuery = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            summaryQuery.where(String.join(" AND ", conditions), arguments.toArray());
        }
        List<BizMaterialIssue> all = issueMapper.selectListByQuery(summaryQuery);
        long pending = 0;
        long completed = 0;
        long amount = 0L;
        for (BizMaterialIssue issue : all) {
            if ("PENDING".equals(issue.getStatus())) {
                pending++;
            } else if ("COMPLETED".equals(issue.getStatus())) {
                completed++;
                // 金额合计只统计已发料单据：待发料单据的金额只是预估，尚未形成成本。
                if (issue.getTotalAmount() != null) {
                    amount += issue.getTotalAmount();
                }
            }
        }
        return new MaterialIssuePageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(),
                new MaterialIssuePageView.Summary(all.size(), pending, completed, amount));
    }

    /** 领料单详情。 */
    @Transactional(readOnly = true)
    public MaterialIssueView get(long id) {
        BizMaterialIssue issue = issueMapper.selectOneById(id);
        if (issue == null) {
            throw new BizException(404, "领料单不存在：" + id);
        }
        return buildViews(List.of(issue)).get(0);
    }

    /**
     * 为工单新建领料单（待发料）。
     *
     * <p>建单不扣库存，也不计入工单材料成本；确认发料时才会扣减库存并计入成本。
     * 领料仓库必须与工单同厂。</p>
     */
    @Transactional
    public MaterialIssueView create(long workOrderId, MaterialIssueCreateCommand command) {
        BizWorkOrder workOrder = workOrderMapper.selectOneById(workOrderId);
        if (workOrder == null) {
            throw new BizException(404, "工单不存在：" + workOrderId);
        }
        BizWarehouse warehouse = warehouseMapper.selectOneById(command.warehouseId());
        if (warehouse == null) {
            throw new BizException(404, "仓库不存在：" + command.warehouseId());
        }
        warehouseService.requireFactoryWrite(workOrder.getFactoryId());
        requireIssueableWorkOrder(workOrder);
        if (!warehouse.getFactoryId().equals(workOrder.getFactoryId())) {
            throw new BizException(409, "领料仓库必须与工单同厂");
        }
        if (!WarehouseService.WAREHOUSE_ACTIVE.equals(warehouse.getStatus())) {
            throw new BizException(409, "仓库已停用，不能领料：" + warehouse.getWarehouseNo());
        }

        LocalDateTime now = LocalDateTime.now();
        BizMaterialIssue issue = new BizMaterialIssue();
        issue.setIssueNo("MI-" + idGenerator.nextId());
        issue.setWorkOrderId(workOrder.getId());
        issue.setWorkOrderNo(workOrder.getWorkOrderNo());
        issue.setOrderNo(workOrder.getOrderNo());
        issue.setCustomerId(workOrder.getCustomerId());
        issue.setFactoryId(workOrder.getFactoryId());
        issue.setWarehouseId(warehouse.getId());
        issue.setStatus("PENDING");
        issue.setTotalAmount(0L);
        issue.setOperatorUserId(null);
        issue.setRemark(trimToNull(command.remark()));
        issue.setCreatedBy(SecurityUtils.userId());
        issue.setCreatedAt(now);
        issue.setUpdatedBy(SecurityUtils.userId());
        issue.setUpdatedAt(now);
        issue.setVersion(0);
        issueMapper.insert(issue);

        long total = 0L;
        for (MaterialIssueCreateCommand.Line line : command.lines()) {
            BizMaterial material = materialMapper.selectOneById(line.materialId());
            if (material == null) {
                throw new BizException(404, "物料不存在：" + line.materialId());
            }
            if (!"ACTIVE".equals(material.getStatus())) {
                throw new BizException(409, "物料已停用，不能领料：" + material.getMaterialCode());
            }
            BigDecimal quantity = WarehouseService.normalizeQuantity(line.quantity());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(400, "领料数量必须大于 0");
            }
            BigDecimal unitPrice = WarehouseService.normalizePrice(material.getUnitPrice());
            BizMaterialIssueLine entity = new BizMaterialIssueLine();
            entity.setIssueId(issue.getId());
            entity.setWorkOrderId(workOrder.getId());
            entity.setMaterialId(material.getId());
            entity.setMaterialCode(material.getMaterialCode());
            entity.setMaterialName(material.getMaterialName());
            entity.setUnit(material.getUnit());
            entity.setQuantity(quantity);
            entity.setUnitPrice(unitPrice);
            entity.setAmount(WarehouseService.amountInCents(quantity, unitPrice));
            entity.setLossRate(WarehouseService.normalizeLossRate(material.getLossRate()));
            entity.setRemark(trimToNull(line.remark()));
            lineMapper.insert(entity);
            total += entity.getAmount();
        }
        issue.setTotalAmount(total);
        issueMapper.update(issue);
        return buildViews(List.of(issue)).get(0);
    }

    /**
     * 确认发料：扣减仓库库存并计入工单材料成本。
     *
     * <p>库存不足时整单失败并回滚，不会出现部分扣减；同一张领料单不能重复发料。</p>
     */
    @Transactional
    public MaterialIssueView issue(long id) {
        BizMaterialIssue issue = issueMapper.selectForUpdate(id);
        if (issue == null) {
            throw new BizException(404, "领料单不存在：" + id);
        }
        if (!"PENDING".equals(issue.getStatus())) {
            throw new BizException(409, "只有待发料的领料单可以发料，当前状态：" + issue.getStatus());
        }
        BizWorkOrder workOrder = workOrderMapper.selectOneById(issue.getWorkOrderId());
        if (workOrder == null) {
            throw new BizException(404, "工单不存在：" + issue.getWorkOrderId());
        }
        BizWarehouse warehouse = warehouseMapper.selectOneById(issue.getWarehouseId());
        if (warehouse == null) {
            throw new BizException(404, "仓库不存在：" + issue.getWarehouseId());
        }
        warehouseService.requireFactoryWrite(issue.getFactoryId());
        requireIssueableWorkOrder(workOrder);
        if (!WarehouseService.WAREHOUSE_ACTIVE.equals(warehouse.getStatus())) {
            throw new BizException(409, "仓库已停用，不能发料：" + warehouse.getWarehouseNo());
        }
        List<BizMaterialIssueLine> lines = lineMapper.selectListByQuery(QueryWrapper.create()
                .where("issue_id = ?", issue.getId())
                .orderBy("id", true));
        if (lines.isEmpty()) {
            throw new BizException(409, "领料单没有明细，无法发料");
        }
        LocalDateTime now = LocalDateTime.now();
        long total = 0L;
        for (BizMaterialIssueLine line : lines) {
            int updated = inventoryMapper.decrease(warehouse.getId(), line.getMaterialId(), line.getQuantity(), now);
            if (updated == 0) {
                throw new BizException(409, "仓库库存不足：" + line.getMaterialCode()
                        + "，需要 " + line.getQuantity().toPlainString() + " " + line.getUnit());
            }
            total += line.getAmount();
        }
        issue.setStatus("COMPLETED");
        issue.setTotalAmount(total);
        issue.setIssuedAt(now);
        issue.setOperatorUserId(SecurityUtils.userId());
        issue.setOperatorName(SecurityUtils.nickname());
        issue.setUpdatedBy(SecurityUtils.userId());
        issue.setUpdatedAt(now);
        issueMapper.update(issue);
        return buildViews(List.of(issue)).get(0);
    }

    /** 取消待发料的领料单。 */
    @Transactional
    public MaterialIssueView cancel(long id) {
        BizMaterialIssue issue = issueMapper.selectForUpdate(id);
        if (issue == null) {
            throw new BizException(404, "领料单不存在：" + id);
        }
        if (!"PENDING".equals(issue.getStatus())) {
            throw new BizException(409, "只有待发料的领料单可以取消，当前状态：" + issue.getStatus());
        }
        warehouseService.requireFactoryWrite(issue.getFactoryId());
        issue.setStatus("CANCELLED");
        issue.setUpdatedBy(SecurityUtils.userId());
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.update(issue);
        return buildViews(List.of(issue)).get(0);
    }

    /**
     * 工单材料成本（分）。
     *
     * <p>只统计已发料的领料单明细；没有成本时返回 0。</p>
     */
    @Transactional(readOnly = true)
    public long materialCostOf(long workOrderId) {
        return materialCostsOf(Set.of(workOrderId)).getOrDefault(workOrderId, 0L);
    }

    /**
     * 批量计算工单材料成本（分）。
     *
     * <p>先取已发料领料单，再汇总其明细金额，避免逐个工单查询。</p>
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> materialCostsOf(Set<Long> workOrderIds) {
        Map<Long, Long> costs = new HashMap<>();
        if (workOrderIds == null || workOrderIds.isEmpty()) {
            return costs;
        }
        List<Long> ids = new ArrayList<>(workOrderIds);
        List<BizMaterialIssue> issues = issueMapper.selectListByQuery(QueryWrapper.create()
                .where("status = 'COMPLETED' AND work_order_id IN ("
                        + placeholders(ids.size()) + ")", ids.toArray()));
        if (issues.isEmpty()) {
            return costs;
        }
        List<Long> issueIds = issues.stream().map(BizMaterialIssue::getId).toList();
        List<BizMaterialIssueLine> lines = lineMapper.selectListByQuery(QueryWrapper.create()
                .where("issue_id IN (" + placeholders(issueIds.size()) + ")", issueIds.toArray()));
        for (BizMaterialIssueLine line : lines) {
            long amount = line.getAmount() == null ? 0L : line.getAmount();
            costs.merge(line.getWorkOrderId(), amount, Long::sum);
        }
        return costs;
    }

    /** 校验工单可以领料：已分配设备且未完成。 */
    private void requireIssueableWorkOrder(BizWorkOrder workOrder) {
        if (workOrder.getDeviceId() == null) {
            throw new BizException(409, "工单尚未分配设备，不能领料：" + workOrder.getWorkOrderNo());
        }
        if (!ISSUEABLE_WORK_ORDER_STATUSES.contains(workOrder.getStatus())) {
            throw new BizException(409, "只有排队中或生产中的工单可以领料，当前状态：" + workOrder.getStatus());
        }
    }

    /** 批量构建视图，补齐仓库与明细。 */
    private List<MaterialIssueView> buildViews(List<BizMaterialIssue> issues) {
        if (issues.isEmpty()) {
            return List.of();
        }
        List<Long> issueIds = issues.stream().map(BizMaterialIssue::getId).toList();
        List<BizMaterialIssueLine> allLines = lineMapper.selectListByQuery(QueryWrapper.create()
                .where("issue_id IN (" + placeholders(issueIds.size()) + ")", issueIds.toArray())
                .orderBy("id", true));
        Map<Long, List<BizMaterialIssueLine>> linesByIssue = new LinkedHashMap<>();
        for (BizMaterialIssueLine line : allLines) {
            linesByIssue.computeIfAbsent(line.getIssueId(), key -> new ArrayList<>()).add(line);
        }
        List<Long> warehouseIds = issues.stream().map(BizMaterialIssue::getWarehouseId).distinct().toList();
        Map<Long, BizWarehouse> warehouses = new LinkedHashMap<>();
        for (BizWarehouse warehouse : warehouseMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(warehouseIds.size()) + ")", warehouseIds.toArray()))) {
            warehouses.put(warehouse.getId(), warehouse);
        }
        List<MaterialIssueView> views = new ArrayList<>(issues.size());
        for (BizMaterialIssue issue : issues) {
            BizWarehouse warehouse = warehouses.get(issue.getWarehouseId());
            List<MaterialIssueView.Line> lineViews = new ArrayList<>();
            for (BizMaterialIssueLine line : linesByIssue.getOrDefault(issue.getId(), List.of())) {
                lineViews.add(new MaterialIssueView.Line(line.getId(), line.getMaterialId(),
                        line.getMaterialCode(), line.getMaterialName(), line.getUnit(), line.getQuantity(),
                        line.getUnitPrice(), line.getAmount(), line.getLossRate(), line.getRemark()));
            }
            views.add(MaterialIssueView.from(issue,
                    warehouse == null ? null : warehouse.getWarehouseNo(),
                    warehouse == null ? null : warehouse.getWarehouseName(), lineViews));
        }
        return views;
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
