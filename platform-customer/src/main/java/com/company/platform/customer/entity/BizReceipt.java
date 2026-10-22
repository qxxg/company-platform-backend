package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户收款单，对应 {@code biz_receipt}。
 *
 * <p>收款登记与核销分开：先登记客户实际到账金额，再按未结应收逐笔分配。
 * 未分配完的余额作为预收/待认领，不能直接抵减客户信用，必须在核销后才会
 * 减少应收余额。金额统一以分存储，禁止使用浮点数。</p>
 *
 * <p>本表只记录公司内部收款，不对接银行直连、第三方支付或电子回单。作废/
 * 红冲需要审批，当前阶段不提供物理删除。</p>
 */
@Table("biz_receipt")
public class BizReceipt {

    /** 收款单主键，自增；页面单号按 SK-{id} 展示。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 客户 ID。 */
    private Long customerId;
    /** 币种，默认 CNY。 */
    private String currency;
    /** 收款金额，单位：分。 */
    private Long amount;
    /** 已核销金额，单位：分，不能超过收款金额。 */
    private Long allocatedAmount;
    /** 收款日期。 */
    private LocalDate receivedDate;
    /** 收款方式：BANK_TRANSFER/ACCEPTANCE/CASH/OTHER。 */
    private String method;
    /** 收款账户或开户行快照。 */
    private String bankAccount;
    /** 银行流水号/票据号，便于对账。 */
    private String serialNo;
    /** 凭证地址；当前只保存链接，不做文件上传。 */
    private String attachmentUrl;
    /** 状态：UNALLOCATED/PARTIAL/ALLOCATED/CANCELLED。 */
    private String status;
    /** 备注。 */
    private String remark;
    /** 登记人。 */
    private Long createdBy;
    /** 登记时间。 */
    private LocalDateTime createdAt;
    /** 最后修改人。 */
    private Long updatedBy;
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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Long getAllocatedAmount() {
        return allocatedAmount;
    }

    public void setAllocatedAmount(Long allocatedAmount) {
        this.allocatedAmount = allocatedAmount;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDate receivedDate) {
        this.receivedDate = receivedDate;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
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
