package com.company.platform.customer.web;

/**
 * 客户信用辅助视图。
 *
 * <p>这里只返回应收侧数据。信用占用的另一半“已确认未发货订单”由订单服务
 * 按分片汇总，再由订单服务和前端合并，避免客户服务反向依赖订单库。</p>
 *
 * @param customerId               客户 ID
 * @param creditLimit              信用额度，单位分
 * @param uncollectedReceivable    未收应收，单位分
 * @param maxOverdueDays           最大逾期天数；没有应收时为 0
 * @param paymentDays              账期天数
 * @param status                   客户状态
 * @param riskLevel                风险等级
 */
public record CustomerCreditView(
        Long customerId,
        Long creditLimit,
        Long uncollectedReceivable,
        Integer maxOverdueDays,
        Integer paymentDays,
        String status,
        String riskLevel
) {
}
