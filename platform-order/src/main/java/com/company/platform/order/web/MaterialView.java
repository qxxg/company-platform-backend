package com.company.platform.order.web;

import com.company.platform.order.entity.BizMaterial;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料视图。
 *
 * <p>stockQuantity 为该物料在全部仓库的库存合计，便于物料档案页判断可用量；
 * 单价最多 4 位小数、损耗率 2 位小数，与档案一致。</p>
 */
public record MaterialView(
        Long id,
        String materialCode,
        String materialName,
        String materialType,
        String spec,
        String unit,
        BigDecimal unitPrice,
        BigDecimal lossRate,
        BigDecimal safetyStock,
        String status,
        String remark,
        BigDecimal stockQuantity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 从实体转换视图。
     *
     * @param material      物料实体
     * @param stockQuantity 全部仓库库存合计，可为空
     */
    public static MaterialView from(BizMaterial material, BigDecimal stockQuantity) {
        return new MaterialView(material.getId(), material.getMaterialCode(), material.getMaterialName(),
                material.getMaterialType(), material.getSpec(), material.getUnit(), material.getUnitPrice(),
                material.getLossRate(), material.getSafetyStock(), material.getStatus(), material.getRemark(),
                stockQuantity, material.getCreatedAt(), material.getUpdatedAt());
    }
}
