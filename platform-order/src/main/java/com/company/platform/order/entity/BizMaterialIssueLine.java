package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.math.BigDecimal;

/**
 * 工单领料明细实体，对应单表 {@code t_material_issue_line}。
 *
 * <p>明细保留物料编码、名称、单价和损耗率快照；行金额 = 数量 × 单价，按分截断。
 * 工单材料成本由已发料领料单的明细汇总得出，不单独落库。</p>
 */
@Table("t_material_issue_line")
public class BizMaterialIssueLine {

    /** 明细主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 领料单 ID。 */
    private Long issueId;
    /** 工单 ID，冗余便于按工单汇总成本。 */
    private Long workOrderId;
    /** 物料 ID。 */
    private Long materialId;
    /** 物料编码快照。 */
    private String materialCode;
    /** 物料名称快照。 */
    private String materialName;
    /** 单位快照。 */
    private String unit;
    /** 领料数量，2 位小数。 */
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

    public Long getIssueId() {
        return issueId;
    }

    public void setIssueId(Long issueId) {
        this.issueId = issueId;
    }

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId) {
        this.workOrderId = workOrderId;
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
