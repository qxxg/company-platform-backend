package com.company.platform.order.web;

/**
 * 设备台账汇总。
 *
 * @param total          设备总数
 * @param idle           空闲
 * @param running        生产中
 * @param maintenance    保养中
 * @param fault          故障
 * @param disabled       停用
 * @param queuedWorkOrders 当前筛选工厂下等待中的工单数
 */
public record DeviceSummaryView(long total, long idle, long running, long maintenance, long fault,
                                long disabled, long queuedWorkOrders) {
}
