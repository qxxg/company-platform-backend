package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工借款单，对应 {@code biz_loan}。
 *
 * <p>借款与费用报销是两类独立单据，借款不并入报销。审批通过只表示“可放款”，
 * 财务实际放款后进入还款中；还款支持现金/转账登记，也支持在报销付款时用
 * 未还借款冲销。金额单位统一为分。</p>
 */
@Table("biz_loan")
public class BizLoan {

    /** 主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 借款单号：JK-雪花号。 */
    private String loanNo;
    /** 借款人用户 ID。 */
    private Long borrowerUserId;
    /** 借款人姓名快照。 */
    private String borrowerName;
    /** 借款金额，单位：分。 */
    private Long amount;
    /** 借款日期。 */
    private LocalDate loanDate;
    /** 借款用途。 */
    private String purpose;
    /** 预计还款日期，可空。 */
    private LocalDate expectedRepayDate;
    /** 归属工厂，可空。 */
    private Long factoryId;
    /** 归属部门/职能。 */
    private String department;
    /** 状态：DRAFT/PENDING/APPROVED/DISBURSED/REPAID/REJECTED/CANCELLED。 */
    private String status;
    /** 审批实例 ID。 */
    private Long approvalInstanceId;
    /** 最近一次审批意见。 */
    private String approvalNote;
    /** 放款方式：CASH/TRANSFER。 */
    private String disburseMethod;
    /** 放款经办人。 */
    private Long disbursedBy;
    /** 放款时间。 */
    private LocalDateTime disbursedAt;
    /** 累计已还金额，单位：分，含报销冲销。 */
    private Long repaidAmount;
    /** 未还金额，单位：分。 */
    private Long outstandingAmount;
    /** 创建人。 */
    private Long createdBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后修改人。 */
    private Long updatedBy;
    /** 最后修改时间。 */
    private LocalDateTime updatedAt;
    /** 版本号，预留给乐观锁。 */
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLoanNo() {
        return loanNo;
    }

    public void setLoanNo(String loanNo) {
        this.loanNo = loanNo;
    }

    public Long getBorrowerUserId() {
        return borrowerUserId;
    }

    public void setBorrowerUserId(Long borrowerUserId) {
        this.borrowerUserId = borrowerUserId;
    }

    public String getBorrowerName() {
        return borrowerName;
    }

    public void setBorrowerName(String borrowerName) {
        this.borrowerName = borrowerName;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public LocalDate getLoanDate() {
        return loanDate;
    }

    public void setLoanDate(LocalDate loanDate) {
        this.loanDate = loanDate;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public LocalDate getExpectedRepayDate() {
        return expectedRepayDate;
    }

    public void setExpectedRepayDate(LocalDate expectedRepayDate) {
        this.expectedRepayDate = expectedRepayDate;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getApprovalInstanceId() {
        return approvalInstanceId;
    }

    public void setApprovalInstanceId(Long approvalInstanceId) {
        this.approvalInstanceId = approvalInstanceId;
    }

    public String getApprovalNote() {
        return approvalNote;
    }

    public void setApprovalNote(String approvalNote) {
        this.approvalNote = approvalNote;
    }

    public String getDisburseMethod() {
        return disburseMethod;
    }

    public void setDisburseMethod(String disburseMethod) {
        this.disburseMethod = disburseMethod;
    }

    public Long getDisbursedBy() {
        return disbursedBy;
    }

    public void setDisbursedBy(Long disbursedBy) {
        this.disbursedBy = disbursedBy;
    }

    public LocalDateTime getDisbursedAt() {
        return disbursedAt;
    }

    public void setDisbursedAt(LocalDateTime disbursedAt) {
        this.disbursedAt = disbursedAt;
    }

    public Long getRepaidAmount() {
        return repaidAmount;
    }

    public void setRepaidAmount(Long repaidAmount) {
        this.repaidAmount = repaidAmount;
    }

    public Long getOutstandingAmount() {
        return outstandingAmount;
    }

    public void setOutstandingAmount(Long outstandingAmount) {
        this.outstandingAmount = outstandingAmount;
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
