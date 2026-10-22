package com.company.platform.order.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 新建设备请求。
 *
 * <p>设备编号全公司唯一，由设备管理员按公司编码规则录入；所属工厂必填，
 * 设备不允许无工厂。能力字段留空表示“不限”，一旦填写则参与排产匹配。</p>
 */
public record DeviceCreateCommand(
        /** 设备编号，例如 EQ-F001-01。 */
        @NotBlank(message = "deviceNo is required")
        @Size(max = 64, message = "deviceNo is too long")
        @Pattern(regexp = "[A-Za-z0-9_-]{2,64}", message = "deviceNo contains illegal characters")
        String deviceNo,
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
        /** 所属工厂。 */
        @NotNull(message = "factoryId is required")
        Long factoryId,
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
        /** 产能：米/小时，必须大于 0。 */
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
