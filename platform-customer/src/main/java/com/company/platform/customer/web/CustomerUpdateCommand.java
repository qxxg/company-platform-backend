package com.company.platform.customer.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 修改客户请求。
 *
 * <p>客户归属、客户名称、信用额度、结算方式和开票信息属于需要审批的字段，
 * 此命令故意不包含这些字段。服务端还比对现有值，防止通过其他接口绕过审批。
 * 审批中心上线后，再由专门的审批单据修改。</p>
 */
public record CustomerUpdateCommand(
        /** 客户简称。 */
        @Size(max = 64, message = "客户简称不能超过 64 个字符") String shortName,
        /** 客户类型。 */
        @Size(max = 32, message = "客户类型不能超过 32 个字符") String customerType,
        /** 统一社会信用代码；修改后仍需查重。 */
        @Size(max = 32, message = "统一社会信用代码不能超过 32 个字符") String creditCode,
        /** 所属行业。 */
        @Size(max = 64, message = "行业不能超过 64 个字符") String industry,
        /** 所属区域。 */
        @Size(max = 64, message = "区域不能超过 64 个字符") String region,
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
        /** 常用生产工厂。 */
        Long defaultFactoryId,
        /** 客户状态；变更会做角色校验，黑名单仅管理员/财务可设置。 */
        @Size(max = 32, message = "客户状态不能超过 32 个字符") String status,
        /** 风险等级。 */
        @Size(max = 32, message = "风险等级不能超过 32 个字符") String riskLevel,
        /** 首次合作日期。 */
        LocalDate firstCooperationDate,
        /** 备注。 */
        @Size(max = 500, message = "备注不能超过 500 个字符") String remark
) {
}
