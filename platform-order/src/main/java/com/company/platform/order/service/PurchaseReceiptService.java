package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.id.SnowflakeIdGenerator;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.entity.BizMaterial;
import com.company.platform.order.entity.BizPurchaseReceipt;
import com.company.platform.order.entity.BizPurchaseReceiptLine;
import com.company.platform.order.entity.BizWarehouse;
import com.company.platform.order.mapper.BizInventoryMapper;
import com.company.platform.order.mapper.BizMaterialMapper;
import com.company.platform.order.mapper.BizPurchaseReceiptLineMapper;
import com.company.platform.order.mapper.BizPurchaseReceiptMapper;
import com.company.platform.order.mapper.BizWarehouseMapper;
import com.company.platform.order.web.PurchaseReceiptCreateCommand;
import com.company.platform.order.web.PurchaseReceiptPageView;
import com.company.platform.order.web.PurchaseReceiptView;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 采购入库服务。
 *
 * <p><strong>材料成本唯一来源：</strong>采购入库单是库存增加的唯一路径。入库单
 * 先建草稿（DRAFT），确认收货（RECEIVED）时按物料档案快照单价、损耗率并计算
 * 行金额（数量 × 单价，按分截断），同时增加库存。已收货单据不可修改，也不
 * 提供作废/红冲（审批中心上线前无通道）。</p>
 *
 * <p>收货时库存按条件写入，重复收货会因为单据状态校验被拒绝，不会重复增加
 * 库存。</p>
 */
@Service
public class PurchaseReceiptService {

    private final BizPurchaseReceiptMapper receiptMapper;
    private final BizPurchaseReceiptLineMapper lineMapper;
    private final BizWarehouseMapper warehouseMapper;
    private final BizMaterialMapper materialMapper;
    private final BizInventoryMapper inventoryMapper;
    private final WarehouseService warehouseService;
    private final SnowflakeIdGenerator idGenerator;

    public PurchaseReceiptService(BizPurchaseReceiptMapper receiptMapper,
                                  BizPurchaseReceiptLineMapper lineMapper,
                                  BizWarehouseMapper warehouseMapper,
                                  BizMaterialMapper materialMapper,
                                  BizInventoryMapper inventoryMapper,
                                  WarehouseService warehouseService,
                                  @Value("${app.snowflake.worker-id:1}") long workerId,
                                  @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        this.receiptMapper = receiptMapper;
        this.lineMapper = lineMapper;
        this.warehouseMapper = warehouseMapper;
        this.materialMapper = materialMapper;
        this.inventoryMapper = inventoryMapper;
        this.warehouseService = warehouseService;
        this.idGenerator = new SnowflakeIdGenerator(workerId, datacenterId);
    }

    /**
     * 采购入库单分页，附带汇总指标。
     *
     * @param pageNumber 页码
     * @param pageSize   每页条数
     * @param keyword    单号/供应商关键字
     * @param factoryId  工厂过滤
     * @param warehouseId 仓库过滤
     * @param status     状态过滤
     */
    @Transactional(readOnly = true)
    public PurchaseReceiptPageView page(long pageNumber, long pageSize, String keyword,
                                        Long factoryId, Long warehouseId, String status) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            conditions.add("(receipt_no LIKE ? OR supplier_name LIKE ?)");
            arguments.add(like);
            arguments.add(like);
        }
        Long effectiveFactory = warehouseService.effectiveFactory(factoryId);
        if (effectiveFactory != null) {
            conditions.add("factory_id = ?");
            arguments.add(effectiveFactory);
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
        Page<BizPurchaseReceipt> page = receiptMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<PurchaseReceiptView> records = buildViews(page.getRecords());

        QueryWrapper summaryQuery = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            summaryQuery.where(String.join(" AND ", conditions), arguments.toArray());
        }
        List<BizPurchaseReceipt> all = receiptMapper.selectListByQuery(summaryQuery);
        long received = 0;
        long draft = 0;
        long amount = 0L;
        for (BizPurchaseReceipt receipt : all) {
            if ("RECEIVED".equals(receipt.getStatus())) {
                received++;
            } else if ("DRAFT".equals(receipt.getStatus())) {
                draft++;
            }
            if (receipt.getTotalAmount() != null) {
                amount += receipt.getTotalAmount();
            }
        }
        return new PurchaseReceiptPageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(),
                new PurchaseReceiptPageView.Summary(all.size(), received, draft, amount));
    }

    /** 入库单详情。 */
    @Transactional(readOnly = true)
    public PurchaseReceiptView get(long id) {
        BizPurchaseReceipt receipt = receiptMapper.selectOneById(id);
        if (receipt == null) {
            throw new BizException(404, "入库单不存在：" + id);
        }
        return buildViews(List.of(receipt)).get(0);
    }

    /**
     * 新建采购入库单（草稿）。
     *
     * <p>行明细在创建时按物料档案快照编码、名称、单位和单价；收货时再按快照
     * 单价计算金额并增加库存。</p>
     */
    @Transactional
    public PurchaseReceiptView create(PurchaseReceiptCreateCommand command) {
        BizWarehouse warehouse = warehouseMapper.selectOneById(command.warehouseId());
        if (warehouse == null) {
            throw new BizException(404, "仓库不存在：" + command.warehouseId());
        }
        warehouseService.requireFactoryWrite(warehouse.getFactoryId());
        if (!WarehouseService.WAREHOUSE_ACTIVE.equals(warehouse.getStatus())) {
            throw new BizException(409, "仓库已停用，不能收货：" + warehouse.getWarehouseNo());
        }
        if (command.receiptDate().isAfter(java.time.LocalDate.now())) {
            throw new BizException(400, "收货日期不能晚于今天");
        }

        LocalDateTime now = LocalDateTime.now();
        BizPurchaseReceipt receipt = new BizPurchaseReceipt();
        receipt.setReceiptNo("PIN-" + idGenerator.nextId());
        receipt.setFactoryId(warehouse.getFactoryId());
        receipt.setWarehouseId(warehouse.getId());
        receipt.setSupplierName(command.supplierName().trim());
        receipt.setReceiptDate(command.receiptDate());
        receipt.setTotalAmount(0L);
        receipt.setStatus("DRAFT");
        receipt.setRemark(trimToNull(command.remark()));
        receipt.setCreatedBy(SecurityUtils.userId());
        receipt.setCreatedAt(now);
        receipt.setUpdatedBy(SecurityUtils.userId());
        receipt.setUpdatedAt(now);
        receipt.setVersion(0);
        receiptMapper.insert(receipt);

        long total = 0L;
        for (PurchaseReceiptCreateCommand.Line line : command.lines()) {
            BizMaterial material = materialMapper.selectOneById(line.materialId());
            if (material == null) {
                throw new BizException(404, "物料不存在：" + line.materialId());
            }
            if (!"ACTIVE".equals(material.getStatus())) {
                throw new BizException(409, "物料已停用，不能入库：" + material.getMaterialCode());
            }
            BigDecimal quantity = WarehouseService.normalizeQuantity(line.quantity());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(400, "入库数量必须大于 0");
            }
            BigDecimal unitPrice = WarehouseService.normalizePrice(material.getUnitPrice());
            BizPurchaseReceiptLine entity = new BizPurchaseReceiptLine();
            entity.setReceiptId(receipt.getId());
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
        receipt.setTotalAmount(total);
        receiptMapper.update(receipt);
        return buildViews(List.of(receipt)).get(0);
    }

    /**
     * 确认收货：按明细增加库存，单据变为已入库。
     *
     * <p>只有草稿状态可以收货；收货后单据不可再修改，也不再提供作废通道。</p>
     */
    @Transactional
    public PurchaseReceiptView receive(long id) {
        BizPurchaseReceipt receipt = receiptMapper.selectForUpdate(id);
        if (receipt == null) {
            throw new BizException(404, "入库单不存在：" + id);
        }
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new BizException(409, "只有草稿状态的入库单可以收货，当前状态：" + receipt.getStatus());
        }
        BizWarehouse warehouse = warehouseMapper.selectOneById(receipt.getWarehouseId());
        if (warehouse == null) {
            throw new BizException(404, "仓库不存在：" + receipt.getWarehouseId());
        }
        warehouseService.requireFactoryWrite(warehouse.getFactoryId());
        if (!WarehouseService.WAREHOUSE_ACTIVE.equals(warehouse.getStatus())) {
            throw new BizException(409, "仓库已停用，不能收货：" + warehouse.getWarehouseNo());
        }
        List<BizPurchaseReceiptLine> lines = lineMapper.selectListByQuery(QueryWrapper.create()
                .where("receipt_id = ?", receipt.getId())
                .orderBy("id", true));
        if (lines.isEmpty()) {
            throw new BizException(409, "入库单没有明细，无法收货");
        }
        LocalDateTime now = LocalDateTime.now();
        long total = 0L;
        for (BizPurchaseReceiptLine line : lines) {
            long amount = WarehouseService.amountInCents(line.getQuantity(), line.getUnitPrice());
            line.setAmount(amount);
            lineMapper.update(line);
            total += amount;
            inventoryMapper.increase(warehouse.getId(), warehouse.getFactoryId(), line.getMaterialId(),
                    line.getQuantity(), now);
        }
        receipt.setStatus("RECEIVED");
        receipt.setTotalAmount(total);
        receipt.setUpdatedBy(SecurityUtils.userId());
        receipt.setUpdatedAt(now);
        receiptMapper.update(receipt);
        return buildViews(List.of(receipt)).get(0);
    }

    /** 取消草稿状态的入库单；已入库单据不可取消。 */
    @Transactional
    public PurchaseReceiptView cancel(long id) {
        BizPurchaseReceipt receipt = receiptMapper.selectForUpdate(id);
        if (receipt == null) {
            throw new BizException(404, "入库单不存在：" + id);
        }
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new BizException(409, "只有草稿状态的入库单可以取消，当前状态：" + receipt.getStatus());
        }
        BizWarehouse warehouse = warehouseMapper.selectOneById(receipt.getWarehouseId());
        if (warehouse != null) {
            warehouseService.requireFactoryWrite(warehouse.getFactoryId());
        }
        receipt.setStatus("CANCELLED");
        receipt.setUpdatedBy(SecurityUtils.userId());
        receipt.setUpdatedAt(LocalDateTime.now());
        receiptMapper.update(receipt);
        return buildViews(List.of(receipt)).get(0);
    }

    /** 批量构建视图，补齐仓库与明细。 */
    private List<PurchaseReceiptView> buildViews(List<BizPurchaseReceipt> receipts) {
        if (receipts.isEmpty()) {
            return List.of();
        }
        List<Long> receiptIds = receipts.stream().map(BizPurchaseReceipt::getId).toList();
        List<BizPurchaseReceiptLine> allLines = lineMapper.selectListByQuery(QueryWrapper.create()
                .where("receipt_id IN (" + placeholders(receiptIds.size()) + ")", receiptIds.toArray())
                .orderBy("id", true));
        Map<Long, List<BizPurchaseReceiptLine>> linesByReceipt = new LinkedHashMap<>();
        for (BizPurchaseReceiptLine line : allLines) {
            linesByReceipt.computeIfAbsent(line.getReceiptId(), key -> new ArrayList<>()).add(line);
        }
        List<Long> warehouseIds = receipts.stream().map(BizPurchaseReceipt::getWarehouseId).distinct().toList();
        Map<Long, BizWarehouse> warehouses = new LinkedHashMap<>();
        for (BizWarehouse warehouse : warehouseMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(warehouseIds.size()) + ")", warehouseIds.toArray()))) {
            warehouses.put(warehouse.getId(), warehouse);
        }
        List<PurchaseReceiptView> views = new ArrayList<>(receipts.size());
        for (BizPurchaseReceipt receipt : receipts) {
            BizWarehouse warehouse = warehouses.get(receipt.getWarehouseId());
            List<PurchaseReceiptView.Line> lineViews = new ArrayList<>();
            for (BizPurchaseReceiptLine line : linesByReceipt.getOrDefault(receipt.getId(), List.of())) {
                lineViews.add(new PurchaseReceiptView.Line(line.getId(), line.getMaterialId(),
                        line.getMaterialCode(), line.getMaterialName(), line.getUnit(), line.getQuantity(),
                        line.getUnitPrice(), line.getAmount(), line.getLossRate(), line.getRemark()));
            }
            views.add(PurchaseReceiptView.from(receipt,
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
