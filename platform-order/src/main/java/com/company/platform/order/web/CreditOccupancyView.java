package com.company.platform.order.web;

/**
 * 客户订单信用占用视图。
 *
 * <p>信用占用 = 未收应收 + 已确认未发货订单。客户服务只负责应收侧，
 * 订单服务负责本视图中的未关闭订单金额；前端把两者相加得到“已用额度”。</p>
 *
 * <p>金额单位统一为分，订单数用于区分“有占用”与“仅存在零金额测试单”。
 * 客户编号不是订单分片键，本查询会广播到 8 张物理表，数据量大后应改为
 * 汇总表或异步统计。</p>
 *
 * @param customerId      客户 ID
 * @param openOrderAmount 未关闭订单金额合计，单位分
 * @param openOrderCount  未关闭订单数量
 */
public record CreditOccupancyView(
        Long customerId,
        Long openOrderAmount,
        Long openOrderCount
) {
}
