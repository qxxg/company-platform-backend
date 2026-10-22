package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 费用报销单，对应 {@code biz_expense_claim}。
 *
 * <p>报销单由员工本人创建，草稿提交后进入统一审批中心；审批通过只表示
 * “财务可付款”，并不等于已付款。付款由财务登记，支持用未还借款冲销部分
 * 报销金额。金额单位统一为分，禁止浮点数和四舍五入。</p>
 *
 * <p>单据不做物理删除：草稿可取消，进入审批后按审批结论流转。作废/红冲
 * 放在后续财务单据能力中处理，当前只保留状态与备注快照。</p>
 */
@Table("biz_expense_claim")
public class BizExpenseClaim {

    /** 主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 报销单号：BX-雪花号。 */
    private String claimNo;
    /** 报销人用户 ID。 */
    private Long claimantUserId;
    /** 报销人姓名快照。 */
    private String claimantName;
    /** 费用类型：TRAVEL/ENTERTAINMENT/PURCHASE/RENT_UTILITIES/LOGISTICS/OFFICE/MAINTENANCE/OTHER。 */
    private String expenseType;
    /** 报销金额，单位：分。 */
    private Long amount;
    /** 费用发生日期。 */
    private LocalDate expenseDate;
    /** 归属工厂，可空。 */
    private Long factoryId;
    /** 归属部门/职能。 */
    private String department;
    /** 关联订单号，可空，仅用于费用归集。 */
    private Long orderNo;
    /** 关联客户 ID，可空，仅用于费用归集。 */
    private Long customerId;
    /** 费用说明。 */
    private String description;
    /** 凭据地址；当前只保存链接，不做文件上传。 */
    private String attachmentUrl;
    /** 状态：DRAFT/PENDING/APPROVED/PAID/REJECTED/CANCELLED。 */
    private String status;
    /** 审批实例 ID。 */
    private Long approvalInstanceId;
    /** 最近一次审批意见。 */
    private String approvalNote;
    /** 现金实付金额，单位：分；冲销借款时小于报销金额。 */
    private Long paidAmount;
    /** 冲销借款金额，单位：分。 */
    private Long offsetLoanAmount;
    /** 付款方式：CASH/TRANSFER/OFFSET。 */
    private String paymentMethod;
    /** 付款备注。 */
    private String paymentRemark;
    /** 付款经办人。 */
    private Long paidBy;
    /** 付款时间。 */
    private LocalDateTime paidAt;
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

    public String getClaimNo() {
        return claimNo;
    }

    public void setClaimNo(String claimNo) {
        this.claimNo = claimNo;
    }

    public Long getClaimantUserId() {
        return claimantUserId;
    }

    public void setClaimantUserId(Long claimantUserId) {
        this.claimantUserId = claimantUserId;
    }

    public String getClaimantName() {
        return claimantName;
    }

    public void setClaimantName(String claimantName) {
        this.claimantName = claimantName;
    }

    public String getExpenseType() {
        return expenseType;
    }

    public void setExpenseType(String expenseType) {
        this.expenseType = expenseType;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
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

    public Long getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Long orderNo) {
        this.orderNo = orderNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
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

    public Long getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(Long paidAmount) {
        this.paidAmount = paidAmount;
    }

    public Long getOffsetLoanAmount() {
        return offsetLoanAmount;
    }

    public void setOffsetLoanAmount(Long offsetLoanAmount) {
        this.offsetLoanAmount = offsetLoanAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentRemark() {
        return paymentRemark;
    }

    public void setPaymentRemark(String paymentRemark) {
        this.paymentRemark = paymentRemark;
    }

    public Long getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(Long paidBy) {
        this.paidBy = paidBy;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
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
