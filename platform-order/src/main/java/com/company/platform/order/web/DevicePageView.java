package com.company.platform.order.web;

import java.util.List;

/**
 * 设备台账分页结果。
 *
 * <p>汇总指标按当前筛选条件统计，不受分页影响；前端用它们展示设备总数和
 * 各状态数量。</p>
 */
public record DevicePageView(
        List<DeviceView> records,
        long total,
        long pageNumber,
        long pageSize,
        DeviceSummaryView summary
) {
}
