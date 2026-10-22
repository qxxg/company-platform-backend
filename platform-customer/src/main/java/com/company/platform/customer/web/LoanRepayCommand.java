package com.company.platform.customer.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 借款还款登记请求。
 *
 * <p>还款金额不能超过未还余额；还款日期不能晚于今天。报销冲销由付款接口
 * 内部生成 OFFSET 记录，不通过本接口手工登记。</p>
 */
public record LoanRepayCommand(
        /** 本次还款金额，单位分。 */
        @NotNull(message = "还款金额不能为空")
        @Positive(message = "还款金额必须大于 0") Long amount,
        /** 还款日期；为空时取今天。 */
        LocalDate repaymentDate,
        /** 还款方式：CASH/TRANSFER。 */
        @Size(max = 16, message = "还款方式不能超过 16 字") String method,
        /** 备注。 */
        @Size(max = 500, message = "备注不能超过 500 字") String remark
) {
}
