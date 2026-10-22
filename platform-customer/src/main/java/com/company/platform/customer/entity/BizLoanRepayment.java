package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 借款还款记录，对应 {@code biz_loan_repayment}。
 *
 * <p>手工还款与报销冲销共用本表：{@code method=OFFSET} 时关联报销单，
 * 其余情况为现金/转账还款。记录只追加、不修改、不删除，借款余额由记录
 * 累计与借款单上的冗余字段共同维护。</p>
 */
@Table("biz_loan_repayment")
public class BizLoanRepayment {

    /** 主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 还款单号：HK-雪花号。 */
    private String repaymentNo;
    /** 借款单 ID。 */
    private Long loanId;
    /** 借款单号快照。 */
    private String loanNo;
    /** 本次还款金额，单位：分。 */
    private Long amount;
    /** 还款日期。 */
    private LocalDate repaymentDate;
    /** 还款方式：CASH/TRANSFER/OFFSET。 */
    private String method;
    /** 冲销的报销单 ID，仅 OFFSET 有值。 */
    private Long expenseClaimId;
    /** 冲销的报销单号快照。 */
    private String expenseClaimNo;
    /** 备注。 */
    private String remark;
    /** 登记人。 */
    private Long createdBy;
    /** 登记时间。 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRepaymentNo() {
        return repaymentNo;
    }

    public void setRepaymentNo(String repaymentNo) {
        this.repaymentNo = repaymentNo;
    }

    public Long getLoanId() {
        return loanId;
    }

    public void setLoanId(Long loanId) {
        this.loanId = loanId;
    }

    public String getLoanNo() {
        return loanNo;
    }

    public void setLoanNo(String loanNo) {
        this.loanNo = loanNo;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public LocalDate getRepaymentDate() {
        return repaymentDate;
    }

    public void setRepaymentDate(LocalDate repaymentDate) {
        this.repaymentDate = repaymentDate;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Long getExpenseClaimId() {
        return expenseClaimId;
    }

    public void setExpenseClaimId(Long expenseClaimId) {
        this.expenseClaimId = expenseClaimId;
    }

    public String getExpenseClaimNo() {
        return expenseClaimNo;
    }

    public void setExpenseClaimNo(String expenseClaimNo) {
        this.expenseClaimNo = expenseClaimNo;
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
}
