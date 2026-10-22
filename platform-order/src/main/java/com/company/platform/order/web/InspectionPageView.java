package com.company.platform.order.web;

import java.util.List;

/**
 * 点检记录分页视图。
 *
 * <p>records 为当前页数据，summary 为按相同筛选条件统计的汇总指标。</p>
 */
public record InspectionPageView(
        List<InspectionView> records,
        long total,
        long pageNumber,
        long pageSize,
        InspectionSummaryView summary
) {
}
