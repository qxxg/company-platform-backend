package com.company.platform.bi.web;

import java.util.List;

/**
 * 首页工作台与经营大屏的统一返回结构。
 *
 * <p>金额字段仍以分为单位返回，同时提供已经格式化的 {@code display} 文本，前端
 * 不需要自行推导金额格式。不能计算的指标不出现在卡片中，统一放入
 * {@code pendingMetrics} 并标注“待数据源”。</p>
 */
public record BiDashboardView(
        Meta meta,
        List<MetricCard> cards,
        OrderTrend orderTrend,
        List<FactoryComparison> factoryComparison,
        List<StatusBreakdown> orderStatus,
        List<StatusBreakdown> equipmentStatus,
        List<RiskAlert> alerts,
        List<MetricDefinition> pendingMetrics
) {

    /**
     * 看板元数据。
     *
     * @param dataStatus   READY / STALE / NO_DATA
     * @param scopeMode    ORG / FACTORY / SELF
     * @param scopeLabel   页面可直接展示的数据范围说明
     * @param periodMonth  当前统计月份，yyyy-MM
     * @param periodYear   当前统计年份，yyyy
     */
    public record Meta(String generatedAt,
                       String dataUpdatedAt,
                       String dataStatus,
                       String scopeMode,
                       String scopeLabel,
                       String periodMonth,
                       String periodYear,
                       long refreshIntervalSeconds) {
    }

    /**
     * 指标卡。status 为 READY；PENDING_SOURCE 指标不进入卡片，避免用零值或估算值
     * 冒充真实业务指标。
     */
    public record MetricCard(String code,
                             String label,
                             Long value,
                             String unit,
                             String display,
                             String tone,
                             String hint,
                             String link) {
    }

    /** 近 12 个月订单趋势与近 6 个月收款趋势；缺失月份补 0。 */
    public record OrderTrend(List<String> months,
                             List<Long> orderAmounts,
                             List<Long> orderCounts,
                             List<Long> receiptAmounts) {
    }

    /** 工厂对比；厂长只返回本厂，销售只统计本人数据。 */
    public record FactoryComparison(Long factoryId,
                                    String factoryName,
                                    Long orderAmount,
                                    Long orderCount,
                                    Long queueCount,
                                    Long equipmentFaultCount) {
    }

    /** 状态分布，用于环形图/堆叠条。 */
    public record StatusBreakdown(String code, String label, Long count) {
    }

    /** 风险预警，前端底部滚动条和首页风险卡共用。 */
    public record RiskAlert(String type,
                            String title,
                            String detail,
                            Long count,
                            String link,
                            String severity) {
    }

    /** 指标口径说明；前端“指标口径”弹窗使用。 */
    public record MetricDefinition(String code,
                                   String name,
                                   String category,
                                   String unit,
                                   String definition,
                                   String sourceNote,
                                   String status,
                                   String permission) {
    }
}
