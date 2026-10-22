package com.company.platform.order.web;

import java.util.List;

/**
 * 工单领料单分页视图。
 *
 * <p>summary 按相同筛选条件统计：单据数、待发料/已发料数量与领料金额合计（分）。</p>
 */
public record MaterialIssuePageView(
        List<MaterialIssueView> records,
        long total,
        long pageNumber,
        long pageSize,
        Summary summary
) {

    /**
     * 领料单汇总指标。
     *
     * @param total       单据总数
     * @param pending     待发料单据数
     * @param completed   已发料单据数
     * @param totalAmount 领料金额合计：分
     */
    public record Summary(long total, long pending, long completed, long totalAmount) {
    }
}
