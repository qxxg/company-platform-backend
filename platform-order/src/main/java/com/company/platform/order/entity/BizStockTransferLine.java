package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.math.BigDecimal;

/**
 * 跨厂调拨明细实体，对应单表 {@code t_stock_transfer_line}。
 *
 * <p>数量在出库时从来源仓库扣减、入库时在目标仓库增加，明细保留物料快照，
 * 便于历史调拨单据追溯。</p>
 */
@Table("t_stock_transfer_line")
public class BizStockTransferLine {

    /** 明细主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 调拨单 ID。 */
    private Long transferId;
    /** 物料 ID。 */
    private Long materialId;
    /** 物料编码快照。 */
    private String materialCode;
    /** 物料名称快照。 */
    private String materialName;
    /** 单位快照。 */
    private String unit;
    /** 调拨数量，2 位小数。 */
    private BigDecimal quantity;
    /** 备注。 */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTransferId() {
        return transferId;
    }

    public void setTransferId(Long transferId) {
        this.transferId = transferId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
