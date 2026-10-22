package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存台账实体，对应单表 {@code t_inventory}。
 *
 * <p>按“仓库 + 物料”唯一，数量不允许为负。库存只能由采购入库和调拨入库增加，
 * 由调拨出库（以及后续的工单领料）减少，系统不提供库存调整入口，因此没有
 * 期初库存。</p>
 */
@Table("t_inventory")
public class BizInventory {

    /** 库存主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 仓库 ID。 */
    private Long warehouseId;
    /** 仓库所属工厂。 */
    private Long factoryId;
    /** 物料 ID。 */
    private Long materialId;
    /** 当前库存数量。 */
    private BigDecimal quantity;
    /** 最后变动时间。 */
    private LocalDateTime updatedAt;
    /** 版本号，预留乐观锁。 */
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
