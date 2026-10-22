package com.company.platform.order.web;

import com.company.platform.order.entity.BizPurchaseReceipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购入库单视图。
 *
 * <p>总金额与行金额单位为分；行内保留物料编码、名称、单价、损耗率快照，历史
 * 单据不受物料档案后续修改影响。</p>
 */
public record PurchaseReceiptView(
        Long id,
        String receiptNo,
        Long factoryId,
        Long warehouseId,
        String warehouseNo,
        String warehouseName,
        String supplierName,
        LocalDate receiptDate,
        Long totalAmount,
        String status,
        String remark,
        List<Line> lines,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 入库单行视图。
     *
     * @param id           明细 ID
     * @param materialId   物料 ID
     * @param materialCode 物料编码快照
     * @param materialName 物料名称快照
     * @param unit         单位快照
     * @param quantity     数量，2 位小数
     * @param unitPrice    单价快照，4 位小数
     * @param amount       行金额：分
     * @param lossRate     损耗率快照，2 位小数
     * @param remark       行备注
     */
    public record Line(
            Long id,
            Long materialId,
            String materialCode,
            String materialName,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            Long amount,
            BigDecimal lossRate,
            String remark
    ) {
    }

    /**
     * 从实体转换视图。
     *
     * @param receipt      入库单实体
     * @param warehouseNo  仓库编号快照
     * @param warehouseName 仓库名称快照
     * @param lines        明细视图
     */
    public static PurchaseReceiptView from(BizPurchaseReceipt receipt, String warehouseNo,
                                           String warehouseName, List<Line> lines) {
        return new PurchaseReceiptView(receipt.getId(), receipt.getReceiptNo(), receipt.getFactoryId(),
                receipt.getWarehouseId(), warehouseNo, warehouseName, receipt.getSupplierName(),
                receipt.getReceiptDate(), receipt.getTotalAmount(), receipt.getStatus(), receipt.getRemark(),
                lines, receipt.getCreatedBy(), receipt.getCreatedAt(), receipt.getUpdatedAt());
    }
}
