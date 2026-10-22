package com.company.platform.customer.web;

import java.util.List;

/**
 * 发票分页结果。
 *
 * <p>字段命名与 {@code PageResult} 保持一致，并附带按筛选条件汇总的
 * 开票金额和税额，避免前端为了统计再拉全量数据。</p>
 */
public record InvoicePageView(
        List<InvoiceView> records,
        long total,
        long pageNumber,
        long pageSize,
        InvoiceSummaryView summary
) {
}
