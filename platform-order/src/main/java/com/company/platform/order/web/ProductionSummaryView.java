package com.company.platform.order.web;

/**
 * 排产看板汇总。
 *
 * @param deviceCount          设备总数
 * @param idleCount            空闲设备
 * @param runningCount         生产中设备
 * @param maintenanceCount     保养设备
 * @param faultCount           故障设备
 * @param queuedCount          等待中工单数
 * @param runningWorkOrderCount 生产中工单数
 * @param pendingJumpCount     待审批插队申请数
 */
public record ProductionSummaryView(long deviceCount, long idleCount, long runningCount,
                                    long maintenanceCount, long faultCount, long queuedCount,
                                    long runningWorkOrderCount, long pendingJumpCount) {
}
