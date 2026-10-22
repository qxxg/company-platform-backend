package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户应收辅助记录，对应 {@code biz_receivable}。
 *
 * <p>当前阶段既用于计算客户信用占用中的“未收应收”和逾期天数，也作为财务
 * 应收列表、收款核销的基础表。正式应收会在发货/对账模块自动生成；发货模块
 * 上线前允许财务手工补录。本表不允许直接物理删除，后续通过作废/红冲处理。</p>
 */
@Table("biz_receivable")
public class BizReceivable {

    /** 应收主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 客户 ID。 */
    private Long customerId;
    /** 关联合同 ID，可为空（手工其他应收）。 */
    private Long contractId;
    /** 关联订单号，可为空。 */
    private Long orderNo;
    /** 来源：ORDER/SHIPMENT/MANUAL/ADJUSTMENT；当前手工补录固定 MANUAL。 */
    private String sourceType;
    /** 应收金额，单位：分。 */
    private Long amount;
    /** 币种，默认 CNY。 */
    private String currency;
    /** 已收金额，单位：分。 */
    private Long receivedAmount;
    /** 到期日。 */
    private LocalDate dueDate;
    /** 状态：UNPAID/PARTIAL/PAID/CANCELLED；逾期不落库，按到期日实时计算。 */
    private String status;
    /** 业务员用户 ID，用于数据范围。 */
    private Long businessOwnerId;
    /** 备注。 */
    private String remark;
    /** 创建人。 */
    private Long createdBy;
    /** 最后修改人。 */
    private Long updatedBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后修改时间。 */
    private LocalDateTime updatedAt;
    /** 版本号，预留给后续乐观锁。 */
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public Long getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Long orderNo) {
        this.orderNo = orderNo;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Long getReceivedAmount() {
        return receivedAmount;
    }

    public void setReceivedAmount(Long receivedAmount) {
        this.receivedAmount = receivedAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getBusinessOwnerId() {
        return businessOwnerId;
    }

    public void setBusinessOwnerId(Long businessOwnerId) {
        this.businessOwnerId = businessOwnerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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
