package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 手工开票记录，对应 {@code biz_invoice}。
 *
 * <p>V1.4 只要求内部登记，不对接税控或电子发票平台。发票号码由财务手工
 * 录入并全局唯一；金额为价税合计，税额和税率用于核对与报表展示，不参与
 * 应收余额计算。作废/红冲必须关联原发票并走审批，当前只保留字段，不开放
 * 直接修改状态为 VOID/REVERSED 的接口。</p>
 */
@Table("biz_invoice")
public class BizInvoice {

    /** 发票主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 客户 ID。 */
    private Long customerId;
    /** 关联合同 ID，可为空。 */
    private Long contractId;
    /** 关联订单号，可为空。 */
    private Long orderNo;
    /** 发票号码，手工录入，全局唯一。 */
    private String invoiceNumber;
    /** 发票类型：VAT_SPECIAL/VAT_NORMAL/ELECTRONIC/OTHER。 */
    private String invoiceType;
    /** 开票抬头。 */
    private String invoiceTitle;
    /** 纳税人识别号。 */
    private String taxNo;
    /** 价税合计，单位：分。 */
    private Long amount;
    /** 税额，单位：分。 */
    private Long taxAmount;
    /** 税率百分数，如 13.0000。 */
    private BigDecimal taxRate;
    /** 开票日期。 */
    private LocalDate invoiceDate;
    /** 状态：REGISTERED/SENT/SIGNED/VOID/REVERSED。 */
    private String status;
    /** 红冲/作废关联的原发票 ID。 */
    private Long originalInvoiceId;
    /** 发票影像/附件地址。 */
    private String attachmentUrl;
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

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getInvoiceType() {
        return invoiceType;
    }

    public void setInvoiceType(String invoiceType) {
        this.invoiceType = invoiceType;
    }

    public String getInvoiceTitle() {
        return invoiceTitle;
    }

    public void setInvoiceTitle(String invoiceTitle) {
        this.invoiceTitle = invoiceTitle;
    }

    public String getTaxNo() {
        return taxNo;
    }

    public void setTaxNo(String taxNo) {
        this.taxNo = taxNo;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Long getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(Long taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOriginalInvoiceId() {
        return originalInvoiceId;
    }

    public void setOriginalInvoiceId(Long originalInvoiceId) {
        this.originalInvoiceId = originalInvoiceId;
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
