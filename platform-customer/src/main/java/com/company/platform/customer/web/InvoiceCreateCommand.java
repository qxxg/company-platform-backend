package com.company.platform.customer.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 手工开票登记请求。
 *
 * <p>金额单位为分，价税合计与税额都由财务按实际发票录入；服务端只校验
 * 逻辑关系（税额不超过价税合计、税率 0–100），不做价税换算，避免因四舍五入
 * 产生与纸面发票不一致的金额。</p>
 */
public record InvoiceCreateCommand(
        /** 客户 ID。 */
        @NotNull(message = "客户不能为空") Long customerId,
        /** 关联合同 ID，可为空；填写时必须属于该客户。 */
        Long contractId,
        /** 关联订单号，可为空。 */
        Long orderNo,
        /** 发票号码，手工录入，全局唯一。 */
        @NotBlank(message = "发票号码不能为空")
        @Size(max = 64, message = "发票号码不能超过 64 个字符") String invoiceNumber,
        /** 发票类型：VAT_SPECIAL/VAT_NORMAL/ELECTRONIC/OTHER。 */
        @NotBlank(message = "发票类型不能为空") String invoiceType,
        /** 开票抬头。 */
        @NotBlank(message = "开票抬头不能为空")
        @Size(max = 128, message = "开票抬头不能超过 128 个字符") String invoiceTitle,
        /** 纳税人识别号。 */
        @Size(max = 64, message = "纳税人识别号不能超过 64 个字符") String taxNo,
        /** 价税合计，单位分。 */
        @NotNull(message = "开票金额不能为空")
        @Positive(message = "开票金额必须大于 0") Long amount,
        /** 税额，单位分，可为 0。 */
        Long taxAmount,
        /** 税率百分数，如 13 表示 13%。 */
        BigDecimal taxRate,
        /** 开票日期。 */
        @NotNull(message = "开票日期不能为空") LocalDate invoiceDate,
        /** 发票影像/附件地址。 */
        @Size(max = 500, message = "附件地址不能超过 500 个字符") String attachmentUrl,
        /** 备注。 */
        @Size(max = 500, message = "备注不能超过 500 个字符") String remark
) {
}
