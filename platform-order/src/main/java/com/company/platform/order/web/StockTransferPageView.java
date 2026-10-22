package com.company.platform.order.web;

import java.util.List;

/**
 * 跨厂调拨单分页视图。
 *
 * <p>summary 按相同筛选条件统计：单据数、待出库、在途与已入库数量。</p>
 */
public record StockTransferPageView(
        List<StockTransferView> records,
        long total,
        long pageNumber,
        long pageSize,
        Summary summary
) {

    /**
     * 调拨单汇总指标。
     *
     * @param total     单据总数
     * @param pending   待出库单据数
     * @param inTransit 在途单据数
     * @param completed 已入库单据数
     */
    public record Summary(long total, long pending, long inTransit, long completed) {
    }
}
