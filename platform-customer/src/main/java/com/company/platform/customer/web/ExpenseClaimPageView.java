package com.company.platform.customer.web;

import java.util.List;

/**
 * 报销单分页结果，附带当前筛选条件的汇总。
 */
public record ExpenseClaimPageView(
        List<ExpenseClaimView> records,
        long total,
        long pageNumber,
        long pageSize,
        ExpenseClaimSummaryView summary
) {
}
