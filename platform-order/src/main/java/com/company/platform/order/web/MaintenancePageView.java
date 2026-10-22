package com.company.platform.order.web;

import java.util.List;

/**
 * 维护工单分页视图。
 *
 * <p>records 为当前页数据，summary 为按相同筛选条件统计的汇总指标。</p>
 */
public record MaintenancePageView(
        List<MaintenanceView> records,
        long total,
        long pageNumber,
        long pageSize,
        MaintenanceSummaryView summary
) {
}
