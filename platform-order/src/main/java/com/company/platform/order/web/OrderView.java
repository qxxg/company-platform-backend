package com.company.platform.order.web;

import com.company.platform.order.entity.BizOrder;

import java.time.LocalDateTime;

/**
 * 订单只读视图。
 *
 * <p>视图与实体分离，避免直接把持久化对象暴露给前端；Long 类型会被公共
 * Jackson 配置序列化为字符串，防止雪花订单号超过 JavaScript 安全整数范围。</p>
 */
public record OrderView(Long orderNo, Long customerId, Long contractId, Long factoryId, String orderStatus,
                        Long totalAmount, String currency, String remark, Long createdBy, LocalDateTime createdAt,
                        LocalDateTime updatedAt) {

    /** 从实体转换为视图；字段一一对应，不做业务计算。 */
    public static OrderView from(BizOrder order) {
        return new OrderView(order.getOrderNo(), order.getCustomerId(), order.getContractId(), order.getFactoryId(),
                order.getOrderStatus(), order.getTotalAmount(), order.getCurrency(), order.getRemark(),
                order.getCreatedBy(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
