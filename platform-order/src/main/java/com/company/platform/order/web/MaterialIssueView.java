package com.company.platform.order.web;

import com.company.platform.order.entity.BizMaterialIssue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单领料单视图。
 *
 * <p>金额单位为分；明细保留物料编码、名称、单价与损耗率快照，便于追溯历史领料
 * 单据，不受物料档案后续修改影响。</p>
 */
public record MaterialIssueView(
        Long id,
        String issueNo,
        Long workOrderId,
        String workOrderNo,
        Long orderNo,
        Long customerId,
        Long factoryId,
        Long warehouseId,
        String warehouseNo,
        String warehouseName,
        String status,
        Long totalAmount,
        LocalDateTime issuedAt,
        Long operatorUserId,
        String operatorName,
        String remark,
        List<Line> lines,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 领料明细视图。
     *
     * @param id           明细 ID
     * @param materialId   物料 ID
     * @param materialCode 物料编码快照
     * @param materialName 物料名称快照
     * @param unit         单位快照
     * @param quantity     领料数量，2 位小数
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
     * @param issue         领料单实体
     * @param warehouseNo   仓库编号快照
     * @param warehouseName 仓库名称快照
     * @param lines         明细视图
     */
    public static MaterialIssueView from(BizMaterialIssue issue, String warehouseNo, String warehouseName,
                                         List<Line> lines) {
        return new MaterialIssueView(issue.getId(), issue.getIssueNo(), issue.getWorkOrderId(),
                issue.getWorkOrderNo(), issue.getOrderNo(), issue.getCustomerId(), issue.getFactoryId(),
                issue.getWarehouseId(), warehouseNo, warehouseName, issue.getStatus(), issue.getTotalAmount(),
                issue.getIssuedAt(), issue.getOperatorUserId(), issue.getOperatorName(), issue.getRemark(),
                lines, issue.getCreatedBy(), issue.getCreatedAt(), issue.getUpdatedAt());
    }
}
