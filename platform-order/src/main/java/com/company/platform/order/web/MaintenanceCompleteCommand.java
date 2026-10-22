package com.company.platform.order.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 完成维护工单命令。
 *
 * <p>costAmount 单位为分，必须是非负整数；维护费用由公司承担，系统只做成本
 * 记录，不生成应收也不计算设备使用费。downtimeMinutes 不传时按实际开始与
 * 结束时间自动计算。</p>
 */
public record MaintenanceCompleteCommand(
        @NotNull(message = "维护成本不能为空") @Min(value = 0, message = "维护成本不能为负数") Long costAmount,
        @Size(max = 500, message = "成本说明不能超过 500 字") String costNote,
        @Min(value = 0, message = "停机时长不能为负数") Integer downtimeMinutes
) {
}
