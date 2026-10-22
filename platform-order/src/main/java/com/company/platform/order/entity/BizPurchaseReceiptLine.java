package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.math.BigDecimal;

/**
 * 采购入库明细实体，对应单表 {@code t_purchase_receipt_line}。
 *
 * <p>行金额 = 单价 × 数量，按分截断后落库；单价与损耗率在收货时从物料档案
 * 快照，保证历史单据不受档案后续修改影响。</p>
 */
@Table("t_purchase_receipt_line")
public class BizPurchaseReceiptLine {

    /** 明细主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 入库单 ID。 */
    private Long receiptId;
    /** 物料 ID。 */
    private Long materialId;
    /** 物料编码快照。 */
    private String materialCode;
    /** 物料名称快照。 */
    private String materialName;
    /** 单位快照。 */
    private String unit;
    /** 入库数量，2 位小数。 */
    private BigDecimal quantity;
    /** 单价快照，4 位小数。 */
    private BigDecimal unitPrice;
    /** 行金额：分。 */
    private Long amount;
    /** 损耗率快照，2 位小数。 */
    private BigDecimal lossRate;
    /** 备注。 */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        this.receiptId = receiptId;
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

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public BigDecimal getLossRate() {
        return lossRate;
    }

    public void setLossRate(BigDecimal lossRate) {
        this.lossRate = lossRate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
