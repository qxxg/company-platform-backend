package com.company.platform.customer.web;

import java.time.LocalDate;

/**
 * 订单服务下单时需要的合同上下文。
 *
 * <p>只返回合同状态、客户归属、金额和有效期，供订单服务做服务端强校验。
 * 合同剩余额度由订单服务按合同汇总未关闭订单后计算。</p>
 */
public record ContractOrderContext(
        Long contractId,
        String contractNo,
        Long customerId,
        String status,
        Long contractAmount,
        String currency,
        LocalDate startDate,
        LocalDate endDate
) {
}
