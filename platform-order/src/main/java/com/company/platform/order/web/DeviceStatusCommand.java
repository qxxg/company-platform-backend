package com.company.platform.order.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 设备状态变更请求。
 *
 * <p>只允许人工设置空闲、保养、故障、停用；生产中的 RUNNING 由开始工单自动
 * 进入，不能通过本接口伪造。变更原因会写入设备状态日志。</p>
 */
public record DeviceStatusCommand(
        /** 目标状态：IDLE/MAINTENANCE/FAULT/DISABLED。 */
        @NotBlank(message = "status is required")
        @Size(max = 32, message = "status is too long")
        String status,
        /** 变更原因。 */
        @Size(max = 500, message = "reason is too long")
        String reason
) {
}
