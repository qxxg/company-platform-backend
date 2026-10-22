package com.company.platform.customer.web;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 报销付款请求。
 *
 * <p>付款是财务动作，审批通过后才能登记。报销金额可以全部现金支付，也可以
 * 用报销人的未还借款冲销一部分；冲销金额与实付金额之和必须等于报销金额。</p>
 */
public record ExpenseClaimPayCommand(
        /** 付款方式：CASH/TRANSFER；全额冲销时可传 OFFSET。 */
        @Size(max = 16, message = "付款方式不能超过 16 字") String paymentMethod,
        /** 用于冲销的借款单 ID；不冲销时为空。 */
        Long offsetLoanId,
        /** 冲销金额，单位分；为空按 0 处理。 */
        @PositiveOrZero(message = "冲销金额不能为负数") Long offsetLoanAmount,
        /** 付款备注。 */
        @Size(max = 500, message = "付款备注不能超过 500 字") String remark
) {
}
