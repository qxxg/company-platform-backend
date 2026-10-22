package com.company.platform.customer.web;

/**
 * 订单服务下单时需要的客户上下文。
 *
 * <p>该视图只包含下单校验必需的字段，不返回联系人、银行账号等敏感信息。
 * 订单服务会用其中的额度、应收和逾期天数完成信用阻断，并再次校验客户状态。</p>
 */
public record CustomerOrderContext(
        Long customerId,
        String customerName,
        String status,
        Long creditLimit,
        Long uncollectedReceivable,
        Integer maxOverdueDays
) {
}
