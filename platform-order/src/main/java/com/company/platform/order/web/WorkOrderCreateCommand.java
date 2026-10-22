package com.company.platform.order.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建生产工单请求。
 *
 * <p>订单号、材料、工艺、门幅和计划长度由排产人员按订单行填写；一个订单可
 * 创建多个工单。设备可以不填，先进入“待排产”，之后再调用分配设备接口。
 * 工单工厂强制取订单所属工厂，请求中不接收工厂字段，避免跨厂排产。</p>
 */
public record WorkOrderCreateCommand(
        /** 订单号。 */
        @NotNull(message = "orderNo is required")
        Long orderNo,
        /** 可选的初始设备；为空表示待排产。 */
        Long deviceId,
        /** 材料/布料。 */
        @NotBlank(message = "materialType is required")
        @Size(max = 64, message = "materialType is too long")
        String materialType,
        /** 工艺/染料。 */
        @NotBlank(message = "processType is required")
        @Size(max = 64, message = "processType is too long")
        String processType,
        /** 门幅（毫米）。 */
        @NotNull(message = "fabricWidthMm is required")
        @Min(value = 1, message = "fabricWidthMm must be positive")
        Integer fabricWidthMm,
        /** 计划生产长度（米），最多 2 位小数。 */
        @NotNull(message = "plannedMeters is required")
        @DecimalMin(value = "0.01", message = "plannedMeters must be positive")
        @Digits(integer = 10, fraction = 2, message = "plannedMeters scale is invalid")
        BigDecimal plannedMeters,
        /** 备注。 */
        @Size(max = 500, message = "remark is too long")
        String remark
) {
}
