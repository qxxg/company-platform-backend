package com.company.platform.order.service;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.id.SnowflakeIdGenerator;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.entity.BizMaterial;
import com.company.platform.order.entity.BizStockTransfer;
import com.company.platform.order.entity.BizStockTransferLine;
import com.company.platform.order.entity.BizWarehouse;
import com.company.platform.order.mapper.BizInventoryMapper;
import com.company.platform.order.mapper.BizMaterialMapper;
import com.company.platform.order.mapper.BizStockTransferLineMapper;
import com.company.platform.order.mapper.BizStockTransferMapper;
import com.company.platform.order.mapper.BizWarehouseMapper;
import com.company.platform.order.web.StockTransferCreateCommand;
import com.company.platform.order.web.StockTransferPageView;
import com.company.platform.order.web.StockTransferView;
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
 * 跨厂调拨服务。
 *
 * <p>调拨是库存“转移”的唯一通道：出库时从调出仓库扣减，入库时在调入仓库增加，
 * 数量不允许为负。状态流转为 PENDING 待出库 → IN_TRANSIT 在途 → COMPLETED
 * 已入库；只有待出库状态可以取消。</p>
 *
 * <p><strong>跨厂协同：</strong>出库由调出工厂的操作人执行，入库由调入工厂的
 * 操作人执行；管理员可以一步完成。同一工厂内的仓库之间也允许调拨（内部移库）。</p>
 */
@Service
public class StockTransferService {

    private final BizStockTransferMapper transferMapper;
    private final BizStockTransferLineMapper lineMapper;
    private final BizWarehouseMapper warehouseMapper;
    private final BizMaterialMapper materialMapper;
    private final BizInventoryMapper inventoryMapper;
    private final WarehouseService warehouseService;
    private final SnowflakeIdGenerator idGenerator;

    public StockTransferService(BizStockTransferMapper transferMapper,
                                BizStockTransferLineMapper lineMapper,
                                BizWarehouseMapper warehouseMapper,
                                BizMaterialMapper materialMapper,
                                BizInventoryMapper inventoryMapper,
                                WarehouseService warehouseService,
                                @Value("${app.snowflake.worker-id:1}") long workerId,
                                @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        this.transferMapper = transferMapper;
        this.lineMapper = lineMapper;
        this.warehouseMapper = warehouseMapper;
        this.materialMapper = materialMapper;
        this.inventoryMapper = inventoryMapper;
        this.warehouseService = warehouseService;
        this.idGenerator = new SnowflakeIdGenerator(workerId, datacenterId);
    }

    /**
     * 调拨单分页，附带汇总指标。
     *
     * @param pageNumber 页码
     * @param pageSize   每页条数
     * @param keyword    单号关键字
     * @param status     状态过滤
     * @param factoryId  工厂过滤：只看与该工厂相关的调拨（调出或调入）
     */
    @Transactional(readOnly = true)
    public StockTransferPageView page(long pageNumber, long pageSize, String keyword,
                                       String status, Long factoryId) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("transfer_no LIKE ?");
            arguments.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            arguments.add(status.trim().toUpperCase(Locale.ROOT));
        }
        Long effectiveFactory = warehouseService.effectiveFactory(factoryId);
        if (effectiveFactory != null) {
            conditions.add("(from_factory_id = ? OR to_factory_id = ?)");
            arguments.add(effectiveFactory);
            arguments.add(effectiveFactory);
        }
        QueryWrapper query = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            query.where(String.join(" AND ", conditions), arguments.toArray());
        }
        query.orderBy("created_at", false).orderBy("id", false);
        Page<BizStockTransfer> page = transferMapper.paginate(Page.of(pageNumber, pageSize), query);
        List<StockTransferView> records = buildViews(page.getRecords());

        QueryWrapper summaryQuery = QueryWrapper.create();
        if (!conditions.isEmpty()) {
            summaryQuery.where(String.join(" AND ", conditions), arguments.toArray());
        }
        List<BizStockTransfer> all = transferMapper.selectListByQuery(summaryQuery);
        long pending = 0;
        long inTransit = 0;
        long completed = 0;
        for (BizStockTransfer transfer : all) {
            switch (transfer.getStatus() == null ? "" : transfer.getStatus()) {
                case "PENDING" -> pending++;
                case "IN_TRANSIT" -> inTransit++;
                case "COMPLETED" -> completed++;
                default -> {
                    // 已取消单据不计入统计。
                }
            }
        }
        return new StockTransferPageView(records, page.getTotalRow(), page.getPageNumber(), page.getPageSize(),
                new StockTransferPageView.Summary(all.size(), pending, inTransit, completed));
    }

    /** 调拨单详情。 */
    @Transactional(readOnly = true)
    public StockTransferView get(long id) {
        BizStockTransfer transfer = transferMapper.selectOneById(id);
        if (transfer == null) {
            throw new BizException(404, "调拨单不存在：" + id);
        }
        return buildViews(List.of(transfer)).get(0);
    }

    /**
     * 新建调拨单（待出库）。
     *
     * <p>创建时校验仓库启用状态、物料存在且启用，并给出当前可用库存提示；真正
     * 的库存扣减在出库时按条件更新，库存不足会被拒绝。</p>
     */
    @Transactional
    public StockTransferView create(StockTransferCreateCommand command) {
        if (command.fromWarehouseId().equals(command.toWarehouseId())) {
            throw new BizException(400, "调出仓库和调入仓库不能相同");
        }
        BizWarehouse from = warehouseMapper.selectOneById(command.fromWarehouseId());
        BizWarehouse to = warehouseMapper.selectOneById(command.toWarehouseId());
        if (from == null) {
            throw new BizException(404, "调出仓库不存在：" + command.fromWarehouseId());
        }
        if (to == null) {
            throw new BizException(404, "调入仓库不存在：" + command.toWarehouseId());
        }
        warehouseService.requireFactoryWrite(from.getFactoryId());
        if (!WarehouseService.WAREHOUSE_ACTIVE.equals(from.getStatus())
                || !WarehouseService.WAREHOUSE_ACTIVE.equals(to.getStatus())) {
            throw new BizException(409, "仓库已停用，不能调拨");
        }
        LocalDateTime now = LocalDateTime.now();
        BizStockTransfer transfer = new BizStockTransfer();
        transfer.setTransferNo("TR-" + idGenerator.nextId());
        transfer.setFromWarehouseId(from.getId());
        transfer.setFromFactoryId(from.getFactoryId());
        transfer.setToWarehouseId(to.getId());
        transfer.setToFactoryId(to.getFactoryId());
        transfer.setStatus("PENDING");
        transfer.setPlannedDate(command.plannedDate());
        transfer.setRemark(trimToNull(command.remark()));
        transfer.setCreatedBy(SecurityUtils.userId());
        transfer.setCreatedAt(now);
        transfer.setUpdatedBy(SecurityUtils.userId());
        transfer.setUpdatedAt(now);
        transfer.setVersion(0);
        transferMapper.insert(transfer);

        for (StockTransferCreateCommand.Line line : command.lines()) {
            BizMaterial material = materialMapper.selectOneById(line.materialId());
            if (material == null) {
                throw new BizException(404, "物料不存在：" + line.materialId());
            }
            if (!"ACTIVE".equals(material.getStatus())) {
                throw new BizException(409, "物料已停用，不能调拨：" + material.getMaterialCode());
            }
            BigDecimal quantity = WarehouseService.normalizeQuantity(line.quantity());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(400, "调拨数量必须大于 0");
            }
            BizStockTransferLine entity = new BizStockTransferLine();
            entity.setTransferId(transfer.getId());
            entity.setMaterialId(material.getId());
            entity.setMaterialCode(material.getMaterialCode());
            entity.setMaterialName(material.getMaterialName());
            entity.setUnit(material.getUnit());
            entity.setQuantity(quantity);
            entity.setRemark(trimToNull(line.remark()));
            lineMapper.insert(entity);
        }
        return buildViews(List.of(transfer)).get(0);
    }

    /**
     * 出库：扣减调出仓库库存，单据变为在途。
     *
     * <p>库存不足时整单失败，不会出现部分扣减；扣减使用条件更新保证并发安全。</p>
     */
    @Transactional
    public StockTransferView ship(long id) {
        BizStockTransfer transfer = transferMapper.selectForUpdate(id);
        if (transfer == null) {
            throw new BizException(404, "调拨单不存在：" + id);
        }
        if (!"PENDING".equals(transfer.getStatus())) {
            throw new BizException(409, "只有待出库的调拨单可以出库，当前状态：" + transfer.getStatus());
        }
        BizWarehouse from = warehouseMapper.selectOneById(transfer.getFromWarehouseId());
        if (from == null) {
            throw new BizException(404, "调出仓库不存在");
        }
        warehouseService.requireFactoryWrite(from.getFactoryId());
        List<BizStockTransferLine> lines = lineMapper.selectListByQuery(QueryWrapper.create()
                .where("transfer_id = ?", transfer.getId())
                .orderBy("id", true));
        if (lines.isEmpty()) {
            throw new BizException(409, "调拨单没有明细，无法出库");
        }
        LocalDateTime now = LocalDateTime.now();
        for (BizStockTransferLine line : lines) {
            int updated = inventoryMapper.decrease(from.getId(), line.getMaterialId(), line.getQuantity(), now);
            if (updated == 0) {
                throw new BizException(409, "调出仓库库存不足：" + line.getMaterialCode()
                        + "，需要 " + line.getQuantity().toPlainString() + " " + line.getUnit());
            }
        }
        transfer.setStatus("IN_TRANSIT");
        transfer.setShippedAt(now);
        transfer.setUpdatedBy(SecurityUtils.userId());
        transfer.setUpdatedAt(now);
        transferMapper.update(transfer);
        return buildViews(List.of(transfer)).get(0);
    }

    /**
     * 入库：增加调入仓库库存，单据变为已入库。
     *
     * <p>入库由调入工厂执行，管理员可以代为操作。</p>
     */
    @Transactional
    public StockTransferView receive(long id) {
        BizStockTransfer transfer = transferMapper.selectForUpdate(id);
        if (transfer == null) {
            throw new BizException(404, "调拨单不存在：" + id);
        }
        if (!"IN_TRANSIT".equals(transfer.getStatus())) {
            throw new BizException(409, "只有在途的调拨单可以入库，当前状态：" + transfer.getStatus());
        }
        BizWarehouse to = warehouseMapper.selectOneById(transfer.getToWarehouseId());
        if (to == null) {
            throw new BizException(404, "调入仓库不存在");
        }
        warehouseService.requireFactoryWrite(to.getFactoryId());
        List<BizStockTransferLine> lines = lineMapper.selectListByQuery(QueryWrapper.create()
                .where("transfer_id = ?", transfer.getId())
                .orderBy("id", true));
        LocalDateTime now = LocalDateTime.now();
        for (BizStockTransferLine line : lines) {
            inventoryMapper.increase(to.getId(), to.getFactoryId(), line.getMaterialId(),
                    line.getQuantity(), now);
        }
        transfer.setStatus("COMPLETED");
        transfer.setReceivedAt(now);
        transfer.setUpdatedBy(SecurityUtils.userId());
        transfer.setUpdatedAt(now);
        transferMapper.update(transfer);
        return buildViews(List.of(transfer)).get(0);
    }

    /** 取消待出库的调拨单。 */
    @Transactional
    public StockTransferView cancel(long id) {
        BizStockTransfer transfer = transferMapper.selectForUpdate(id);
        if (transfer == null) {
            throw new BizException(404, "调拨单不存在：" + id);
        }
        if (!"PENDING".equals(transfer.getStatus())) {
            throw new BizException(409, "只有待出库的调拨单可以取消，当前状态：" + transfer.getStatus());
        }
        BizWarehouse from = warehouseMapper.selectOneById(transfer.getFromWarehouseId());
        if (from != null) {
            warehouseService.requireFactoryWrite(from.getFactoryId());
        }
        transfer.setStatus("CANCELLED");
        transfer.setUpdatedBy(SecurityUtils.userId());
        transfer.setUpdatedAt(LocalDateTime.now());
        transferMapper.update(transfer);
        return buildViews(List.of(transfer)).get(0);
    }

    /** 批量构建视图，补齐仓库与明细。 */
    private List<StockTransferView> buildViews(List<BizStockTransfer> transfers) {
        if (transfers.isEmpty()) {
            return List.of();
        }
        List<Long> transferIds = transfers.stream().map(BizStockTransfer::getId).toList();
        List<BizStockTransferLine> allLines = lineMapper.selectListByQuery(QueryWrapper.create()
                .where("transfer_id IN (" + placeholders(transferIds.size()) + ")", transferIds.toArray())
                .orderBy("id", true));
        Map<Long, List<BizStockTransferLine>> linesByTransfer = new LinkedHashMap<>();
        for (BizStockTransferLine line : allLines) {
            linesByTransfer.computeIfAbsent(line.getTransferId(), key -> new ArrayList<>()).add(line);
        }
        List<Long> warehouseIds = new ArrayList<>();
        for (BizStockTransfer transfer : transfers) {
            warehouseIds.add(transfer.getFromWarehouseId());
            warehouseIds.add(transfer.getToWarehouseId());
        }
        List<Long> distinctIds = warehouseIds.stream().distinct().toList();
        Map<Long, BizWarehouse> warehouses = new LinkedHashMap<>();
        for (BizWarehouse warehouse : warehouseMapper.selectListByQuery(QueryWrapper.create()
                .where("id IN (" + placeholders(distinctIds.size()) + ")", distinctIds.toArray()))) {
            warehouses.put(warehouse.getId(), warehouse);
        }
        List<StockTransferView> views = new ArrayList<>(transfers.size());
        for (BizStockTransfer transfer : transfers) {
            BizWarehouse from = warehouses.get(transfer.getFromWarehouseId());
            BizWarehouse to = warehouses.get(transfer.getToWarehouseId());
            List<StockTransferView.Line> lineViews = new ArrayList<>();
            for (BizStockTransferLine line : linesByTransfer.getOrDefault(transfer.getId(), List.of())) {
                lineViews.add(new StockTransferView.Line(line.getId(), line.getMaterialId(),
                        line.getMaterialCode(), line.getMaterialName(), line.getUnit(), line.getQuantity(),
                        line.getRemark()));
            }
            views.add(StockTransferView.from(transfer,
                    from == null ? null : from.getWarehouseNo(),
                    from == null ? null : from.getWarehouseName(),
                    to == null ? null : to.getWarehouseNo(),
                    to == null ? null : to.getWarehouseName(), lineViews));
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
