package com.company.platform.order.web;

/**
 * 点检记录汇总指标。
 *
 * <p>按当前筛选条件统计，不受分页影响；abnormal 表示结果为异常的记录数量，
 * 是待安排维护的入口。</p>
 */
public record InspectionSummaryView(
        long total,
        long normal,
        long abnormal,
        long deviceCount
) {
}
