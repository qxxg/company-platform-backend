package com.company.platform.customer.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 财务手工登记应收请求。
 *
 * <p>正式应收应由发货/开票自动生成；当前发货模块尚未上线，先允许财务补录
 * 并强制标记来源 MANUAL。金额单位为分，调用方必须已完成截断，服务端不做
 * 四舍五入。合同和订单均可为空（其他应收），但合同必须属于同一客户。</p>
 */
public record ReceivableCreateCommand(
        /** 客户 ID。 */
        @NotNull(message = "客户不能为空") Long customerId,
        /** 关联合同 ID，可为空。 */
        Long contractId,
        /** 关联订单号，可为空。 */
        Long orderNo,
        /** 应收金额，单位分。 */
        @NotNull(message = "应收金额不能为空")
        @Positive(message = "应收金额必须大于 0") Long amount,
        /** 币种；为空时取客户币种。 */
        @Size(max = 8, message = "币种不能超过 8 个字符") String currency,
        /** 到期日；为空时按客户账期从今天推算。 */
        LocalDate dueDate,
        /** 备注。 */
        @Size(max = 500, message = "备注不能超过 500 个字符") String remark
) {
}
