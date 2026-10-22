package com.company.platform.customer.web;

import java.util.List;

/**
 * 借款单分页结果，附带当前筛选条件的汇总。
 */
public record LoanPageView(
        List<LoanView> records,
        long total,
        long pageNumber,
        long pageSize,
        LoanSummaryView summary
) {
}
