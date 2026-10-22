package com.company.platform.order.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存视图。
 *
 * <p>一条记录表示某仓库某物料的当前数量。belowSafety 标识低于安全库存，页面
 * 用于高亮补货；安全库存按 2 位小数比较。</p>
 */
public record InventoryView(
        Long id,
        Long warehouseId,
        String warehouseNo,
        String warehouseName,
        Long factoryId,
        Long materialId,
        String materialCode,
        String materialName,
        String unit,
        BigDecimal quantity,
        BigDecimal safetyStock,
        boolean belowSafety,
        LocalDateTime updatedAt
) {
}
