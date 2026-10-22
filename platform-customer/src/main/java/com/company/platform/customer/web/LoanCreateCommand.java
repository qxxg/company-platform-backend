package com.company.platform.customer.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 新建借款单请求。
 *
 * <p>借款人固定取当前登录人；金额单位为分。借款与报销是独立单据，审批
 * 通过后仍需财务实际放款。</p>
 */
public record LoanCreateCommand(
        /** 借款金额，单位分。 */
        @NotNull(message = "借款金额不能为空")
        @Positive(message = "借款金额必须大于 0") Long amount,
        /** 借款日期；为空时取今天。 */
        LocalDate loanDate,
        /** 借款用途。 */
        @NotBlank(message = "借款用途不能为空") @Size(max = 500, message = "借款用途不能超过 500 字") String purpose,
        /** 预计还款日期，可空。 */
        LocalDate expectedRepayDate,
        /** 归属工厂，可空。 */
        Long factoryId,
        /** 归属部门/职能，可空。 */
        @Size(max = 64, message = "部门不能超过 64 字") String department
) {
}
