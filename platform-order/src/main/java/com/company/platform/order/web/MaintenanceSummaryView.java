package com.company.platform.order.web;

/**
 * 维护工单汇总指标。
 *
 * <p>按当前筛选条件统计，不受分页影响；成本合计为筛选结果内的维护成本之和，
 * 单位为分（公司承担，不向客户收取）。</p>
 */
public record MaintenanceSummaryView(
        long total,
        long pending,
        long inProgress,
        long completed,
        long cancelled,
        long costAmountTotal
) {
}
