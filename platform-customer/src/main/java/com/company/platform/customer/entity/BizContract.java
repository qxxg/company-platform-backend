package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户合同，对应 {@code biz_contract}。
 *
 * <p>V1.4 规定合同由销售与客户谈定，财务正式录入并编号。只有
 * {@code ENTERED}（已录入）或 {@code ACTIVE}（生效）状态允许订单关联；
 * {@code PENDING_ENTRY} 只是草稿，不能作为下单依据。</p>
 *
 * <p>金额统一为分。合同剩余额度不落冗余字段，由订单服务按合同汇总未关闭
 * 订单后计算，避免订单状态变更时出现两份金额不一致。</p>
 */
@Table("biz_contract")
public class BizContract {

    /** 合同主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 合同编号，财务正式编号且全局唯一。 */
    private String contractNo;
    /** 客户 ID。 */
    private Long customerId;
    /** 合同类型：PROCESSING/FRAMEWORK/ANNUAL_PRICE。 */
    private String contractType;
    /** 合同开始日期。 */
    private LocalDate startDate;
    /** 合同结束日期；为空表示长期有效。 */
    private LocalDate endDate;
    /** 合同金额，单位：分。 */
    private Long contractAmount;
    /** 币种，默认 CNY。 */
    private String currency;
    /** 结算方式。 */
    private String settlementMethod;
    /** 账期天数。 */
    private Integer paymentDays;
    /** 默认税率，例如 0.1300，保留 4 位小数。 */
    private java.math.BigDecimal taxRate;
    /** 价格条款。 */
    private String priceTerms;
    /** 质量条款。 */
    private String qualityTerms;
    /** 合同状态：PENDING_ENTRY/ENTERED/ACTIVE/CHANGING/EXPIRED/TERMINATED。 */
    private String status;
    /** 财务录入人用户 ID。 */
    private Long entryUserId;
    /** 财务录入人姓名快照。 */
    private String entryUserName;
    /** 财务正式录入时间。 */
    private LocalDateTime entryTime;
    /** 合同附件地址；只存路径，不做文件内容存储。 */
    private String attachmentUrl;
    /** 备注。 */
    private String remark;
    /** 创建人用户 ID。 */
    private Long createdBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后修改人用户 ID。 */
    private Long updatedBy;
    /** 最后修改时间。 */
    private LocalDateTime updatedAt;
    /** 乐观锁版本号字段，为后续合同变更版本预留。 */
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContractNo() {
        return contractNo;
    }

    public void setContractNo(String contractNo) {
        this.contractNo = contractNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getContractType() {
        return contractType;
    }

    public void setContractType(String contractType) {
        this.contractType = contractType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Long getContractAmount() {
        return contractAmount;
    }

    public void setContractAmount(Long contractAmount) {
        this.contractAmount = contractAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSettlementMethod() {
        return settlementMethod;
    }

    public void setSettlementMethod(String settlementMethod) {
        this.settlementMethod = settlementMethod;
    }

    public Integer getPaymentDays() {
        return paymentDays;
    }

    public void setPaymentDays(Integer paymentDays) {
        this.paymentDays = paymentDays;
    }

    public java.math.BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(java.math.BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public String getPriceTerms() {
        return priceTerms;
    }

    public void setPriceTerms(String priceTerms) {
        this.priceTerms = priceTerms;
    }

    public String getQualityTerms() {
        return qualityTerms;
    }

    public void setQualityTerms(String qualityTerms) {
        this.qualityTerms = qualityTerms;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getEntryUserId() {
        return entryUserId;
    }

    public void setEntryUserId(Long entryUserId) {
        this.entryUserId = entryUserId;
    }

    public String getEntryUserName() {
        return entryUserName;
    }

    public void setEntryUserName(String entryUserName) {
        this.entryUserName = entryUserName;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
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
