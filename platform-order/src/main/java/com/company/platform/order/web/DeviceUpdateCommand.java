package com.company.platform.order.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 修改设备请求。
 *
 * <p>设备编号和所属工厂不在修改范围内：编号是台账主键，跨厂移机需要双方
 * 厂长审批并更新队列，不能通过普通编辑绕过。运营参数和能力参数可以修改，
 * 但已有活跃队列时修改能力不会自动重排，需由排产人员确认。</p>
 */
public record DeviceUpdateCommand(
        /** 设备名称。 */
        @NotBlank(message = "deviceName is required")
        @Size(max = 128, message = "deviceName is too long")
        String deviceName,
        /** 设备类型。 */
        @NotBlank(message = "deviceType is required")
        @Size(max = 64, message = "deviceType is too long")
        String deviceType,
        /** 型号。 */
        @Size(max = 128, message = "model is too long")
        String model,
        /** 车间/产线。 */
        @Size(max = 128, message = "workshop is too long")
        String workshop,
        /** 详细位置。 */
        @Size(max = 255, message = "location is too long")
        String location,
        /** 可处理材料，逗号分隔。 */
        @Size(max = 500, message = "materialTypes is too long")
        String materialTypes,
        /** 最小门幅（毫米）。 */
        @Min(value = 0, message = "minWidthMm must not be negative")
        Integer minWidthMm,
        /** 最大门幅（毫米）。 */
        @Min(value = 0, message = "maxWidthMm must not be negative")
        Integer maxWidthMm,
        /** 可处理工艺，逗号分隔。 */
        @Size(max = 500, message = "processTypes is too long")
        String processTypes,
        /** 产能：米/小时。 */
        @NotNull(message = "capacityMetersPerHour is required")
        @DecimalMin(value = "0.01", message = "capacityMetersPerHour must be positive")
        @Digits(integer = 8, fraction = 2, message = "capacityMetersPerHour scale is invalid")
        BigDecimal capacityMetersPerHour,
        /** 换型时间（分钟）。 */
        @Min(value = 0, message = "changeoverMinutes must not be negative")
        Integer changeoverMinutes,
        /** 良率百分比。 */
        @DecimalMin(value = "0", message = "yieldRate must not be negative")
        @DecimalMax(value = "100", message = "yieldRate must not exceed 100")
        @Digits(integer = 3, fraction = 2, message = "yieldRate scale is invalid")
        BigDecimal yieldRate,
        /** 操作员资质要求。 */
        @Size(max = 255, message = "operatorRequirement is too long")
        String operatorRequirement,
        /** 备注。 */
        @Size(max = 500, message = "remark is too long")
        String remark
) {
}
