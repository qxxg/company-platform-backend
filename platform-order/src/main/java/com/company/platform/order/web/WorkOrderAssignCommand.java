package com.company.platform.order.web;

import jakarta.validation.constraints.NotNull;

/**
 * 给待排产工单分配设备。
 *
 * <p>只允许 PENDING 状态工单分配设备；分配后立即进入该设备队列并计算预计
 * 开始/完成时间。设备必须与工单同工厂，且状态不能是保养、故障或停用。</p>
 */
public record WorkOrderAssignCommand(
        /** 目标设备 ID。 */
        @NotNull(message = "deviceId is required")
        Long deviceId
) {
}
