package com.company.platform.customer.web;

/**
 * 报销列表汇总。
 *
 * <p>金额为当前筛选条件下的合计，单位分：审批中金额用于提示在途单据，
 * 待付款金额用于财务安排资金。</p>
 */
public record ExpenseClaimSummaryView(
        long count,
        long draftCount,
        long pendingCount,
        long approvedCount,
        long paidCount,
        long rejectedCount,
        Long totalAmount,
        Long pendingAmount,
        Long approvedAmount,
        Long paidAmount
) {
}
