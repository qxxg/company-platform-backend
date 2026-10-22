package com.company.platform.order.mapper;

import com.company.platform.order.entity.BizInventory;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存台账 Mapper。
 *
 * <p>库存是单表（固定 ds_0）。出入库都通过条件更新完成：只有当前数量满足条件
 * 才会更新成功，从而避免并发下把库存扣成负数。</p>
 */
@Mapper
public interface BizInventoryMapper extends BaseMapper<BizInventory> {

    /** 锁定某仓库某物料的库存行；不存在时返回空。 */
    @Select("SELECT * FROM t_inventory WHERE warehouse_id = #{warehouseId} AND material_id = #{materialId} FOR UPDATE")
    BizInventory selectForUpdate(@Param("warehouseId") Long warehouseId,
                                 @Param("materialId") Long materialId);

    /**
     * 增加库存。
     *
     * <p>按“仓库 + 物料”幂等写入：行不存在则插入，存在则累加数量。插入使用
     * {@code INSERT ... ON DUPLICATE KEY UPDATE}，保证并发入库不会丢失数量。</p>
     */
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO t_inventory (warehouse_id, factory_id, material_id, quantity, updated_at, version)
            VALUES (#{warehouseId}, #{factoryId}, #{materialId}, #{quantity}, #{updatedAt}, 0)
            ON DUPLICATE KEY UPDATE quantity = quantity + #{quantity},
                                    updated_at = #{updatedAt},
                                    version = version + 1
            """)
    int increase(@Param("warehouseId") Long warehouseId,
                 @Param("factoryId") Long factoryId,
                 @Param("materialId") Long materialId,
                 @Param("quantity") BigDecimal quantity,
                 @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 扣减库存。
     *
     * <p>只有当前数量足够时才会更新成功，返回 0 表示库存不足，调用方据此给出
     * 业务提示而不是产生负库存。</p>
     */
    @Update("""
            UPDATE t_inventory
            SET quantity = quantity - #{quantity},
                updated_at = #{updatedAt},
                version = version + 1
            WHERE warehouse_id = #{warehouseId}
              AND material_id = #{materialId}
              AND quantity >= #{quantity}
            """)
    int decrease(@Param("warehouseId") Long warehouseId,
                 @Param("materialId") Long materialId,
                 @Param("quantity") BigDecimal quantity,
                 @Param("updatedAt") LocalDateTime updatedAt);
}
