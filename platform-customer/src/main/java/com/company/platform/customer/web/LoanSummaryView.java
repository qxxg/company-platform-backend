package com.company.platform.customer.web;

/**
 * 借款列表汇总。
 *
 * <p>金额为当前筛选条件下的合计，单位分。activeCount 为还款中的借款单数。</p>
 */
public record LoanSummaryView(
        long count,
        long activeCount,
        long repaidCount,
        Long totalAmount,
        Long outstandingAmount,
        Long repaidAmount
) {
}
