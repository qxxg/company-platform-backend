package com.company.platform.customer.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 收款核销请求。
 *
 * <p>一次可以核销多张应收，但总金额不能超过收款单未核销余额；每张应收的
 * 本次核销金额不能超过其未收余额。服务端在事务内会重新加锁校验，不能只相信
 * 前端展示的余额。</p>
 */
public record ReceiptAllocateCommand(
        /** 核销明细，至少一行。 */
        @NotEmpty(message = "核销明细不能为空")
        @Valid List<AllocationLine> allocations
) {

    /**
     * 单张应收的核销金额。
     *
     * @param receivableId 应收 ID
     * @param amount       本次核销金额，单位分
     */
    public record AllocationLine(
            @NotNull(message = "应收 ID 不能为空") Long receivableId,
            @NotNull(message = "核销金额不能为空")
            @Positive(message = "核销金额必须大于 0") Long amount
    ) {
    }
}
