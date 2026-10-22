package com.company.platform.order.web;

import java.util.List;

/**
 * 排产看板：按设备分组展示当前订单和等待队列。
 *
 * @param factoryId 当前筛选工厂；为空表示全部有权限的工厂
 * @param devices   设备队列
 * @param summary   汇总指标
 */
public record ProductionBoardView(Long factoryId, List<DeviceQueueView> devices,
                                  ProductionSummaryView summary) {
}
