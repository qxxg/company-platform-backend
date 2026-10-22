package com.company.platform.customer.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 收款登记请求。
 *
 * <p>只登记实际到账，不直接指定应收；登记后通过核销接口分配到一张或多张
 * 应收。金额单位为分，必须大于 0。未核销余额保留为预收/待认领。</p>
 */
public record ReceiptCreateCommand(
        /** 客户 ID。 */
        @NotNull(message = "客户不能为空") Long customerId,
        /** 收款金额，单位分。 */
        @NotNull(message = "收款金额不能为空")
        @Positive(message = "收款金额必须大于 0") Long amount,
        /** 币种；为空时取客户币种。 */
        @Size(max = 8, message = "币种不能超过 8 个字符") String currency,
        /** 收款日期；为空时取今天。 */
        LocalDate receivedDate,
        /** 收款方式：BANK_TRANSFER/ACCEPTANCE/CASH/OTHER。 */
        @Size(max = 32, message = "收款方式不能超过 32 个字符") String method,
        /** 收款账户/开户行。 */
        @Size(max = 128, message = "收款账户不能超过 128 个字符") String bankAccount,
        /** 银行流水号/票据号。 */
        @Size(max = 64, message = "流水号不能超过 64 个字符") String serialNo,
        /** 凭证地址。 */
        @Size(max = 500, message = "凭证地址不能超过 500 个字符") String attachmentUrl,
        /** 备注。 */
        @Size(max = 500, message = "备注不能超过 500 个字符") String remark
) {
}
