package com.company.platform.order.client;

import java.time.LocalDate;

/**
 * 客户服务返回的合同下单上下文。
 *
 * <p>订单服务用它校验合同状态、客户归属、币种和有效期。合同剩余额度不在
 * 客户服务计算，由订单服务汇总本订单库中同合同的未关闭订单。</p>
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
