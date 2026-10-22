package com.company.platform.order.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 登记设备点检记录命令。
 *
 * <p>工厂不允许前端传入：点检记录必须跟随设备的归属工厂。异常项数不能超过
 * 点检项数，结果为异常时至少要有一项异常。</p>
 */
public record InspectionCreateCommand(
        @NotNull(message = "设备不能为空") Long deviceId,
        @NotBlank(message = "点检周期不能为空") String planType,
        @NotNull(message = "点检日期不能为空") LocalDate inspectionDate,
        @NotBlank(message = "点检结果不能为空") String result,
        @Min(value = 0, message = "点检项数不能为负数") Integer itemCount,
        @Min(value = 0, message = "异常项数不能为负数") Integer abnormalCount,
        @Size(max = 1000, message = "点检内容不能超过 1000 字") String content,
        Long handlerUserId
) {
}
