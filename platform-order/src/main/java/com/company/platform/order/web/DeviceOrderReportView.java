package com.company.platform.order.web;

import com.company.platform.order.entity.BizDeviceOrderReport;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单作业数据视图。
 *
 * <p>设备每 10 秒上报一次，按订单号唯一，最新一次覆盖历史。长度类字段保留设备
 * 上报的 3 位小数，业务计算时再按 2 位小数截取。</p>
 */
public record DeviceOrderReportView(
        Long id,
        Long orderNo,
        String deviceNo,
        String workOrderNo,
        String reportStatus,
        BigDecimal printedLengthMeter,
        BigDecimal progressMeter,
        BigDecimal progressPercent,
        BigDecimal inkUsageMl,
        BigDecimal inkDeltaMl,
        Integer remainingMinutes,
        BigDecimal speedMeterPerHour,
        BigDecimal widthMeter,
        Integer passCount,
        String deviceState,
        String errorCode,
        boolean imageUploaded,
        LocalDateTime reportedAt,
        Long deviceSeq,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 从实体转换视图。 */
    public static DeviceOrderReportView from(BizDeviceOrderReport report) {
        return new DeviceOrderReportView(report.getId(), report.getOrderNo(), report.getDeviceNo(),
                report.getWorkOrderNo(), report.getReportStatus(), report.getPrintedLengthMeter(),
                report.getProgressMeter(), report.getProgressPercent(), report.getInkUsageMl(),
                report.getInkDeltaMl(), report.getRemainingMinutes(), report.getSpeedMeterPerHour(),
                report.getWidthMeter(), report.getPassCount(), report.getDeviceState(), report.getErrorCode(),
                report.getImageUploaded() != null && report.getImageUploaded() == 1, report.getReportedAt(),
                report.getDeviceSeq(), report.getCreatedAt(), report.getUpdatedAt());
    }
}
