package com.company.platform.order.web;

import com.company.platform.order.entity.BizMaintenanceOrder;

import java.time.LocalDateTime;

/**
 * 设备维护工单视图。
 *
 * <p>补齐设备名称和执行人姓名快照，历史单据不随台账改名而变化。成本字段
 * 单位为分，与财务模块口径一致。</p>
 */
public record MaintenanceView(
        Long id,
        String maintenanceNo,
        Long deviceId,
        String deviceNo,
        String deviceName,
        Long factoryId,
        String maintenanceType,
        String title,
        String description,
        String priority,
        String status,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        LocalDateTime actualStart,
        LocalDateTime actualEnd,
        Integer downtimeMinutes,
        Long costAmount,
        String costNote,
        Long handlerUserId,
        String handlerName,
        Long inspectionId,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 从实体转换视图。
     *
     * @param order      维护工单实体
     * @param deviceName 设备名称快照，可为空
     */
    public static MaintenanceView from(BizMaintenanceOrder order, String deviceName) {
        return new MaintenanceView(order.getId(), order.getMaintenanceNo(), order.getDeviceId(),
                order.getDeviceNo(), deviceName, order.getFactoryId(), order.getMaintenanceType(),
                order.getTitle(), order.getDescription(), order.getPriority(), order.getStatus(),
                order.getPlannedStart(), order.getPlannedEnd(), order.getActualStart(), order.getActualEnd(),
                order.getDowntimeMinutes(), order.getCostAmount(), order.getCostNote(),
                order.getHandlerUserId(), order.getHandlerName(), order.getInspectionId(), order.getRemark(),
                order.getCreatedBy(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
