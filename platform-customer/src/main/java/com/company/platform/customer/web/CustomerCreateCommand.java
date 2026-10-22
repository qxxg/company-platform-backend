package com.company.platform.customer.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新建客户请求。
 *
 * <p>客户来源和归属业务员是必填项。业务员只能把客户归属到自己名下；
 * 管理员/财务可按 ownerUserId 指定归属。信用额度只有财务或管理员能设置，
 * 其他角色提交的非零值会被服务端忽略并归零。</p>
 */
public record CustomerCreateCommand(
        /** 客户编码；为空时由服务端生成。 */
        @Size(max = 32, message = "客户编码不能超过 32 个字符") String customerCode,
        /** 客户全称。 */
        @NotBlank(message = "客户名称不能为空")
        @Size(max = 128, message = "客户名称不能超过 128 个字符") String name,
        /** 客户简称。 */
        @Size(max = 64, message = "客户简称不能超过 64 个字符") String shortName,
        /** 客户类型。 */
        @Size(max = 32, message = "客户类型不能超过 32 个字符") String customerType,
        /** 统一社会信用代码。 */
        @Size(max = 32, message = "统一社会信用代码不能超过 32 个字符") String creditCode,
        /** 所属行业。 */
        @Size(max = 64, message = "行业不能超过 64 个字符") String industry,
        /** 所属区域。 */
        @Size(max = 64, message = "区域不能超过 64 个字符") String region,
        /** 客户来源字典编码。 */
        @NotBlank(message = "客户来源不能为空")
        @Size(max = 64, message = "客户来源不能超过 64 个字符") String source,
        /** 归属业务员用户 ID。 */
        @NotNull(message = "归属业务员不能为空") Long ownerUserId,
        /** 归属业务员姓名快照，可选。 */
        @Size(max = 64, message = "业务员姓名不能超过 64 个字符") String ownerName,
        /** 归属部门。 */
        @Size(max = 64, message = "归属部门不能超过 64 个字符") String department,
        /** 主联系人。 */
        @Size(max = 64, message = "联系人不能超过 64 个字符") String contactName,
        /** 主联系人职务。 */
        @Size(max = 64, message = "联系人职务不能超过 64 个字符") String contactTitle,
        /** 主联系人手机号。 */
        @Size(max = 32, message = "手机号不能超过 32 个字符") String contactPhone,
        /** 主联系人邮箱。 */
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过 128 个字符") String contactEmail,
        /** 主联系人微信。 */
        @Size(max = 64, message = "微信不能超过 64 个字符") String contactWechat,
        /** 收货/对账地址。 */
        @Size(max = 255, message = "地址不能超过 255 个字符") String address,
        /** 开票抬头。 */
        @Size(max = 128, message = "开票抬头不能超过 128 个字符") String billingTitle,
        /** 纳税人识别号。 */
        @Size(max = 32, message = "税号不能超过 32 个字符") String taxNo,
        /** 开户行。 */
        @Size(max = 128, message = "开户行不能超过 128 个字符") String bankName,
        /** 银行账号。 */
        @Size(max = 64, message = "银行账号不能超过 64 个字符") String bankAccount,
        /** 结算方式。 */
        @Size(max = 32, message = "结算方式不能超过 32 个字符") String settlementMethod,
        /** 账期天数。 */
        @Min(value = 0, message = "账期不能小于 0") Integer paymentDays,
        /** 信用额度，单位：分；仅财务/管理员可设置。 */
        @PositiveOrZero(message = "信用额度不能小于 0") Long creditLimit,
        /** 默认税率，0~1 之间，最多 4 位小数。 */
        @DecimalMin(value = "0.0000", message = "税率不能小于 0")
        @DecimalMax(value = "1.0000", message = "税率不能大于 1") BigDecimal defaultTaxRate,
        /** 币种。 */
        @Size(max = 8, message = "币种不能超过 8 个字符") String currency,
        /** 常用生产工厂。 */
        Long defaultFactoryId,
        /** 客户状态，默认 PROSPECT。 */
        @Size(max = 32, message = "客户状态不能超过 32 个字符") String status,
        /** 风险等级。 */
        @Size(max = 32, message = "风险等级不能超过 32 个字符") String riskLevel,
        /** 首次合作日期。 */
        LocalDate firstCooperationDate,
        /** 备注。 */
        @Size(max = 500, message = "备注不能超过 500 个字符") String remark
) {
}
