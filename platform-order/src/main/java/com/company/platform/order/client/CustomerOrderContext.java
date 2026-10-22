package com.company.platform.order.client;

/**
 * 客户服务返回的下单上下文，只保留信用校验需要的字段。
 *
 * <p>该记录是订单服务与客户服务之间的内部契约。字段名必须与客户服务
 * {@code CustomerOrderContext} 保持一致，否则 Jackson 反序列化会丢字段。</p>
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
