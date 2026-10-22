package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户对账单快照，对应 {@code biz_statement}。
 *
 * <p>对账单按客户和期间生成，期初、本期应收、本期核销收款、本期开票和期末
 * 在这里固化；确认后归档，不再修改。重新生成会产生新版本，旧版本保留用于
 * 追溯，不做物理删除。</p>
 */
@Table("biz_statement")
public class BizStatement {

    /** 对账单主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 客户 ID。 */
    private Long customerId;
    /** 对账期间开始日。 */
    private LocalDate periodStart;
    /** 对账期间结束日。 */
    private LocalDate periodEnd;
    /** 期初应收余额，单位：分。 */
    private Long openingAmount;
    /** 本期新增应收，单位：分。 */
    private Long receivableAmount;
    /** 本期核销收款，单位：分。 */
    private Long receivedAmount;
    /** 本期开票金额，单位：分（仅参考，不冲应收）。 */
    private Long invoiceAmount;
    /** 期末应收余额，单位：分。 */
    private Long closingAmount;
    /** 状态：GENERATED/CONFIRMED/CANCELLED。 */
    private String status;
    /** 客户确认时的差异说明。 */
    private String differenceNote;
    /** 确认人。 */
    private Long confirmedBy;
    /** 确认时间。 */
    private LocalDateTime confirmedAt;
    /** 备注。 */
    private String remark;
    /** 生成人。 */
    private Long createdBy;
    /** 生成时间。 */
    private LocalDateTime createdAt;
    /** 最后修改人。 */
    private Long updatedBy;
    /** 最后修改时间。 */
    private LocalDateTime updatedAt;
    /** 版本号。 */
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

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Long getOpeningAmount() {
        return openingAmount;
    }

    public void setOpeningAmount(Long openingAmount) {
        this.openingAmount = openingAmount;
    }

    public Long getReceivableAmount() {
        return receivableAmount;
    }

    public void setReceivableAmount(Long receivableAmount) {
        this.receivableAmount = receivableAmount;
    }

    public Long getReceivedAmount() {
        return receivedAmount;
    }

    public void setReceivedAmount(Long receivedAmount) {
        this.receivedAmount = receivedAmount;
    }

    public Long getInvoiceAmount() {
        return invoiceAmount;
    }

    public void setInvoiceAmount(Long invoiceAmount) {
        this.invoiceAmount = invoiceAmount;
    }

    public Long getClosingAmount() {
        return closingAmount;
    }

    public void setClosingAmount(Long closingAmount) {
        this.closingAmount = closingAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDifferenceNote() {
        return differenceNote;
    }

    public void setDifferenceNote(String differenceNote) {
        this.differenceNote = differenceNote;
    }

    public Long getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(Long confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
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
