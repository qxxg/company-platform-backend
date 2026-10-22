package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizCustomer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户只读视图。
 *
 * <p>不直接暴露实体，后续增加字段权限时可以在转换阶段屏蔽开票、银行账号等
 * 敏感信息。Long 类型由公共 Jackson 配置序列化为字符串。</p>
 */
public record CustomerView(
        Long id,
        String customerCode,
        String name,
        String shortName,
        String customerType,
        String creditCode,
        String industry,
        String region,
        String source,
        Long ownerUserId,
        String ownerName,
        String department,
        String contactName,
        String contactTitle,
        String contactPhone,
        String contactEmail,
        String contactWechat,
        String address,
        String billingTitle,
        String taxNo,
        String bankName,
        String bankAccount,
        String settlementMethod,
        Integer paymentDays,
        Long creditLimit,
        BigDecimal defaultTaxRate,
        String currency,
        Long defaultFactoryId,
        String status,
        String riskLevel,
        LocalDate firstCooperationDate,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 实体转视图，字段一一对应，不做金额换算。 */
    public static CustomerView from(BizCustomer customer) {
        return new CustomerView(
                customer.getId(), customer.getCustomerCode(), customer.getName(), customer.getShortName(),
                customer.getCustomerType(), customer.getCreditCode(), customer.getIndustry(), customer.getRegion(),
                customer.getSource(), customer.getOwnerUserId(), customer.getOwnerName(), customer.getDepartment(),
                customer.getContactName(), customer.getContactTitle(), customer.getContactPhone(),
                customer.getContactEmail(), customer.getContactWechat(), customer.getAddress(),
                customer.getBillingTitle(), customer.getTaxNo(), customer.getBankName(), customer.getBankAccount(),
                customer.getSettlementMethod(), customer.getPaymentDays(), customer.getCreditLimit(),
                customer.getDefaultTaxRate(), customer.getCurrency(), customer.getDefaultFactoryId(),
                customer.getStatus(), customer.getRiskLevel(), customer.getFirstCooperationDate(),
                customer.getRemark(), customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
