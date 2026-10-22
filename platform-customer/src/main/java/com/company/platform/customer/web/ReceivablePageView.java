package com.company.platform.customer.web;

import java.util.List;

/**
 * 应收分页结果。
 *
 * <p>在标准分页字段之外附带按筛选条件汇总的金额指标，前端不用为了统计
 * 再拉取全量数据。字段命名与 {@code PageResult} 保持一致，便于复用表格
 * 和分页组件。</p>
 */
public record ReceivablePageView(
        List<ReceivableView> records,
        long total,
        long pageNumber,
        long pageSize,
        ReceivableSummaryView summary
) {
}
