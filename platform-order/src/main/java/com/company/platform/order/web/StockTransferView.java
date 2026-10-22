package com.company.platform.order.web;

import com.company.platform.order.entity.BizStockTransfer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨厂调拨单视图。
 *
 * <p>同时展示调出、调入仓库及其工厂，便于跨厂协同确认在途物料。</p>
 */
public record StockTransferView(
        Long id,
        String transferNo,
        Long fromWarehouseId,
        String fromWarehouseNo,
        String fromWarehouseName,
        Long fromFactoryId,
        Long toWarehouseId,
        String toWarehouseNo,
        String toWarehouseName,
        Long toFactoryId,
        String status,
        LocalDate plannedDate,
        LocalDateTime shippedAt,
        LocalDateTime receivedAt,
        String remark,
        List<Line> lines,
        Long createdBy,
        LocalDateTime createdAt
) {

    /**
     * 调拨单行视图。
     *
     * @param id           明细 ID
     * @param materialId   物料 ID
     * @param materialCode 物料编码快照
     * @param materialName 物料名称快照
     * @param unit         单位快照
     * @param quantity     调拨数量，2 位小数
     * @param remark       行备注
     */
    public record Line(
            Long id,
            Long materialId,
            String materialCode,
            String materialName,
            String unit,
            BigDecimal quantity,
            String remark
    ) {
    }

    /**
     * 从实体转换视图。
     *
     * @param transfer         调拨单实体
     * @param fromWarehouseNo  调出仓库编号
     * @param fromWarehouseName 调出仓库名称
     * @param toWarehouseNo    调入仓库编号
     * @param toWarehouseName  调入仓库名称
     * @param lines            明细视图
     */
    public static StockTransferView from(BizStockTransfer transfer, String fromWarehouseNo,
                                          String fromWarehouseName, String toWarehouseNo,
                                          String toWarehouseName, List<Line> lines) {
        return new StockTransferView(transfer.getId(), transfer.getTransferNo(),
                transfer.getFromWarehouseId(), fromWarehouseNo, fromWarehouseName, transfer.getFromFactoryId(),
                transfer.getToWarehouseId(), toWarehouseNo, toWarehouseName, transfer.getToFactoryId(),
                transfer.getStatus(), transfer.getPlannedDate(), transfer.getShippedAt(),
                transfer.getReceivedAt(), transfer.getRemark(), lines, transfer.getCreatedBy(),
                transfer.getCreatedAt());
    }
}
