package com.company.platform.approval.web;

import java.util.List;

/**
 * 审批单分页视图。
 *
 * <p>summary 按相同筛选条件统计：单据总数、待审批、已通过与已驳回数量。</p>
 */
public record ApprovalPageView(
        List<ApprovalView> records,
        long total,
        long pageNumber,
        long pageSize,
        Summary summary
) {

    /**
     * 审批单汇总指标。
     *
     * @param total    单据总数
     * @param pending  待审批数量
     * @param approved 已通过数量
     * @param rejected 已驳回数量
     */
    public record Summary(long total, long pending, long approved, long rejected) {
    }
}
