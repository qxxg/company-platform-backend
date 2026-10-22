package com.company.platform.order.web;

import com.company.platform.order.entity.BizWarehouse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 仓库视图。
 *
 * <p>补齐库存概览（物料行数、库存总量），便于档案页直接判断仓库负载；库存数量
 * 保留 2 位小数。</p>
 */
public record WarehouseView(
        Long id,
        String warehouseNo,
        String warehouseName,
        Long factoryId,
        String warehouseType,
        String address,
        Long managerUserId,
        String managerName,
        String status,
        String remark,
        Integer materialCount,
        BigDecimal quantityTotal,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 从实体转换视图。
     *
     * @param warehouse   仓库实体
     * @param managerName 负责人姓名快照，可为空
     * @param materialCount 库存物料行数
     * @param quantityTotal  库存总量
     */
    public static WarehouseView from(BizWarehouse warehouse, String managerName,
                                     Integer materialCount, BigDecimal quantityTotal) {
        return new WarehouseView(warehouse.getId(), warehouse.getWarehouseNo(), warehouse.getWarehouseName(),
                warehouse.getFactoryId(), warehouse.getWarehouseType(), warehouse.getAddress(),
                warehouse.getManagerUserId(), managerName, warehouse.getStatus(), warehouse.getRemark(),
                materialCount, quantityTotal, warehouse.getCreatedAt(), warehouse.getUpdatedAt());
    }
}
