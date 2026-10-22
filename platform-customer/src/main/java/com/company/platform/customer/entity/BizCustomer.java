package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户主数据，对应 {@code biz_customer}。
 *
 * <p>客户来源和归属业务员是 V1.4 规定的必填项；客户状态决定能否下单，
 * 只有“合作中”的客户才允许创建订单。客户名称、信用额度、结算方式等
 * 敏感字段的变更需要审批，服务层会在更新时做保护。</p>
 *
 * <p>信用额度单位固定为分。0 表示尚未授予信用额度，任何赊销订单都会被
 * 阻断，必须由财务维护后才可下单。</p>
 */
@Table("biz_customer")
public class BizCustomer {

    /** 客户主键，自增；历史演示订单使用 3001/4001/9001 固定编号。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 客户编码，全局唯一，由服务端自动生成或由管理员录入。 */
    private String customerCode;
    /** 客户全称。 */
    private String name;
    /** 客户简称，用于列表和订单展示。 */
    private String shortName;
    /** 客户类型：ENTERPRISE/INDIVIDUAL/OTHER。 */
    private String customerType;
    /** 统一社会信用代码，用于查重和开票。 */
    private String creditCode;
    /** 所属行业。 */
    private String industry;
    /** 所属区域。 */
    private String region;
    /** 客户来源字典编码，必填。 */
    private String source;
    /** 归属业务员用户 ID，必填。 */
    private Long ownerUserId;
    /** 归属业务员姓名快照，避免跨服务查询影响列表展示。 */
    private String ownerName;
    /** 归属部门。 */
    private String department;
    /** 主联系人姓名。 */
    private String contactName;
    /** 主联系人职务。 */
    private String contactTitle;
    /** 主联系人手机号。 */
    private String contactPhone;
    /** 主联系人邮箱。 */
    private String contactEmail;
    /** 主联系人微信。 */
    private String contactWechat;
    /** 收货/对账地址。 */
    private String address;
    /** 开票抬头。 */
    private String billingTitle;
    /** 纳税人识别号。 */
    private String taxNo;
    /** 开户行。 */
    private String bankName;
    /** 银行账号。 */
    private String bankAccount;
    /** 结算方式：PREPAID/MONTHLY/ACCEPTANCE/OTHER。 */
    private String settlementMethod;
    /** 账期天数。 */
    private Integer paymentDays;
    /** 信用额度，单位：分；0 表示未授予额度。 */
    private Long creditLimit;
    /** 默认税率，例如 0.1300，保留 4 位小数。 */
    private java.math.BigDecimal defaultTaxRate;
    /** 币种，默认 CNY。 */
    private String currency;
    /** 常用生产工厂 ID，仅作默认值，不限制订单实际工厂。 */
    private Long defaultFactoryId;
    /** 客户状态：PROSPECT/COOPERATING/SUSPENDED/LOST/BLACKLIST。 */
    private String status;
    /** 风险等级：NORMAL/WATCH/HIGH。 */
    private String riskLevel;
    /** 首次合作日期。 */
    private LocalDate firstCooperationDate;
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
    /** 乐观锁版本号字段，为后续并发更新预留。 */
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getCreditCode() {
        return creditCode;
    }

    public void setCreditCode(String creditCode) {
        this.creditCode = creditCode;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactTitle() {
        return contactTitle;
    }

    public void setContactTitle(String contactTitle) {
        this.contactTitle = contactTitle;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactWechat() {
        return contactWechat;
    }

    public void setContactWechat(String contactWechat) {
        this.contactWechat = contactWechat;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getBillingTitle() {
        return billingTitle;
    }

    public void setBillingTitle(String billingTitle) {
        this.billingTitle = billingTitle;
    }

    public String getTaxNo() {
        return taxNo;
    }

    public void setTaxNo(String taxNo) {
        this.taxNo = taxNo;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
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

    public Long getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(Long creditLimit) {
        this.creditLimit = creditLimit;
    }

    public java.math.BigDecimal getDefaultTaxRate() {
        return defaultTaxRate;
    }

    public void setDefaultTaxRate(java.math.BigDecimal defaultTaxRate) {
        this.defaultTaxRate = defaultTaxRate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Long getDefaultFactoryId() {
        return defaultFactoryId;
    }

    public void setDefaultFactoryId(Long defaultFactoryId) {
        this.defaultFactoryId = defaultFactoryId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public LocalDate getFirstCooperationDate() {
        return firstCooperationDate;
    }

    public void setFirstCooperationDate(LocalDate firstCooperationDate) {
        this.firstCooperationDate = firstCooperationDate;
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
