package com.company.platform.customer.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 财务录入合同请求。
 *
 * <p>合同金额单位为分。新建后默认是“待财务录入”，必须调用录入接口后才会
 * 变成“已录入”，订单才能引用。这样销售草稿不会误占用合同额度。</p>
 */
public record ContractCreateCommand(
        /** 财务正式合同编号，必填且唯一。 */
        @NotBlank(message = "合同编号不能为空")
        @Size(max = 64, message = "合同编号不能超过 64 个字符") String contractNo,
        /** 客户 ID。 */
        @NotNull(message = "客户不能为空") Long customerId,
        /** 合同类型：PROCESSING/FRAMEWORK/ANNUAL_PRICE。 */
        @Size(max = 32, message = "合同类型不能超过 32 个字符") String contractType,
        /** 开始日期。 */
        LocalDate startDate,
        /** 结束日期；为空表示长期。 */
        LocalDate endDate,
        /** 合同金额，单位分。 */
        @NotNull(message = "合同金额不能为空")
        @Positive(message = "合同金额必须大于 0") Long contractAmount,
        /** 币种。 */
        @Size(max = 8, message = "币种不能超过 8 个字符") String currency,
        /** 结算方式。 */
        @Size(max = 32, message = "结算方式不能超过 32 个字符") String settlementMethod,
        /** 账期天数。 */
        @Min(value = 0, message = "账期不能小于 0") Integer paymentDays,
        /** 税率，0~1。 */
        @DecimalMin(value = "0.0000", message = "税率不能小于 0")
        @DecimalMax(value = "1.0000", message = "税率不能大于 1") BigDecimal taxRate,
        /** 价格条款。 */
        @Size(max = 2000, message = "价格条款不能超过 2000 个字符") String priceTerms,
        /** 质量条款。 */
        @Size(max = 2000, message = "质量条款不能超过 2000 个字符") String qualityTerms,
        /** 附件地址。 */
        @Size(max = 500, message = "附件地址不能超过 500 个字符") String attachmentUrl,
        /** 备注。 */
        @Size(max = 500, message = "备注不能超过 500 个字符") String remark
) {
}
