package com.company.platform.customer.web;

/**
 * 发票汇总指标。
 *
 * <p>按当前筛选条件统计，不受分页限制；作废和红冲发票不进入开票总额，
 * 但仍计入张数，方便财务核对。</p>
 *
 * @param totalAmount 开票总额（价税合计），单位分
 * @param taxAmount   税额合计，单位分
 * @param count       发票张数
 * @param validCount  有效发票张数（不含 VOID/REVERSED）
 */
public record InvoiceSummaryView(
        long totalAmount,
        long taxAmount,
        long count,
        long validCount
) {
}
