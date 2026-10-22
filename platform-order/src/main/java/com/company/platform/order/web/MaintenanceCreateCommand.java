package com.company.platform.order.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 新建设备维护工单命令。
 *
 * <p>工厂不允许前端传入：维护工单必须跟随设备的归属工厂，避免跨厂维保。
 * 执行人默认为当前登录用户。inspectionId 由点检异常生成工单时回填，用于从
 * 工单回溯点检记录。</p>
 */
public record MaintenanceCreateCommand(
        @NotNull(message = "设备不能为空") Long deviceId,
        @NotBlank(message = "维护类型不能为空") String maintenanceType,
        @NotBlank(message = "维护主题不能为空") @Size(max = 200, message = "维护主题不能超过 200 字") String title,
        @Size(max = 1000, message = "问题描述不能超过 1000 字") String description,
        @NotBlank(message = "优先级不能为空") String priority,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        Long handlerUserId,
        Long inspectionId,
        @Size(max = 500, message = "备注不能超过 500 字") String remark
) {
}
