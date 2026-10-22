package com.company.platform.customer.web;

/**
 * 应收汇总视图，按当前筛选条件统计，不受分页限制。
 *
 * <p>金额均为分。逾期金额只统计“到期日早于今天且仍未收完”的应收；已取消
 * 应收不进入任何汇总。当前实现为服务层汇总，数据量增大后应改为 SQL 聚合
 * 或按日离线统计。</p>
 *
 * @param totalAmount       应收总额
 * @param receivedAmount    已收总额
 * @param outstandingAmount 未收总额
 * @param overdueAmount     逾期未收金额
 * @param overdueCount      逾期应收笔数
 * @param openCount         未结清应收笔数
 * @param customerCount     涉及客户数
 */
public record ReceivableSummaryView(
        Long totalAmount,
        Long receivedAmount,
        Long outstandingAmount,
        Long overdueAmount,
        long overdueCount,
        long openCount,
        long customerCount
) {
}
