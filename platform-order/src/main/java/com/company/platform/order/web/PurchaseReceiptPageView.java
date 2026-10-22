package com.company.platform.order.web;

import java.util.List;

/**
 * 采购入库单分页视图。
 *
 * <p>summary 按相同筛选条件统计：单据数、已入库/草稿数量与入库总金额（分）。</p>
 */
public record PurchaseReceiptPageView(
        List<PurchaseReceiptView> records,
        long total,
        long pageNumber,
        long pageSize,
        Summary summary
) {

    /**
     * 入库单汇总指标。
     *
     * @param total        单据总数
     * @param received     已入库单据数
     * @param draft        草稿单据数
     * @param totalAmount  入库总金额：分
     */
    public record Summary(long total, long received, long draft, long totalAmount) {
    }
}
