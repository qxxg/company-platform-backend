package com.company.platform.customer.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 客户信用额度落地命令。
 *
 * <p>由审批中心在审批通过后调用：额度属于审批字段，页面不能直接改数。金额单位
 * 为分，必须是非负整数。</p>
 */
public record CreditLimitApplyCommand(
        @NotNull(message = "信用额度不能为空") @Min(value = 0, message = "信用额度不能为负数") Long creditLimit
) {
}
