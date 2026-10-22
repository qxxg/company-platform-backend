package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料档案实体，对应单表 {@code t_material}。
 *
 * <p>物料是采购入库、库存和后续工单领料的共同基础。单价最多 4 位小数、损耗率
 * 2 位小数，由数据库 DECIMAL 约束；金额在单据层按分存储并截断。</p>
 */
@Table("t_material")
public class BizMaterial {

    /** 物料主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 物料编码，全公司唯一。 */
    private String materialCode;
    /** 物料名称。 */
    private String materialName;
    /** 物料类型：FABRIC/CHEMICAL/ACCESSORY。 */
    private String materialType;
    /** 规格。 */
    private String spec;
    /** 计量单位。 */
    private String unit;
    /** 采购单价，最多 4 位小数。 */
    private BigDecimal unitPrice;
    /** 损耗率（百分比），2 位小数。 */
    private BigDecimal lossRate;
    /** 安全库存。 */
    private BigDecimal safetyStock;
    /** 状态：ACTIVE/DISABLED。 */
    private String status;
    /** 备注。 */
    private String remark;
    /** 创建人。 */
    private Long createdBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后修改人。 */
    private Long updatedBy;
    /** 最后修改时间。 */
    private LocalDateTime updatedAt;
    /** 版本号，预留乐观锁。 */
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public String getMaterialType() {
        return materialType;
    }

    public void setMaterialType(String materialType) {
        this.materialType = materialType;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLossRate() {
        return lossRate;
    }

    public void setLossRate(BigDecimal lossRate) {
        this.lossRate = lossRate;
    }

    public BigDecimal getSafetyStock() {
        return safetyStock;
    }

    public void setSafetyStock(BigDecimal safetyStock) {
        this.safetyStock = safetyStock;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
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
