package com.company.platform.order.web;

import com.company.platform.order.entity.BizDevice;
import com.company.platform.order.entity.BizDeviceQueue;
import com.company.platform.order.entity.BizWorkOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单只读视图，包含设备和队列位置。
 *
 * <p>队列位置基于同一设备活跃队列按 queueSeq 排序后实时计算，不落库，避免
 * 插队重排时维护冗余位置字段。已完成的工单保留历史队列信息，但位置为空。</p>
 */
public record WorkOrderView(
        Long id,
        String workOrderNo,
        Long orderNo,
        Long customerId,
        Long salesUserId,
        Long factoryId,
        Long deviceId,
        String deviceNo,
        String deviceName,
        String materialType,
        String processType,
        Integer fabricWidthMm,
        BigDecimal plannedMeters,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        LocalDateTime actualStart,
        LocalDateTime actualEnd,
        String status,
        Long queueId,
        Long queueSeq,
        String queueStatus,
        Integer queuePosition,
        LocalDateTime enqueuedAt,
        Boolean jumped,
        String jumpReason,
        Long materialCostAmount,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 从工单、设备和队列组合视图。
     *
     * @param workOrder     工单实体
     * @param device        设备，可为空（待排产）
     * @param queue         队列记录，可为空（待排产/已取消）
     * @param queuePosition 实时队列位置，从 1 开始；非活跃队列为空
     * @param materialCost  工单材料成本：分；无成本查看权限时为 null
     */
    public static WorkOrderView from(BizWorkOrder workOrder, BizDevice device, BizDeviceQueue queue,
                                     Integer queuePosition, Long materialCost) {
        return new WorkOrderView(workOrder.getId(), workOrder.getWorkOrderNo(), workOrder.getOrderNo(),
                workOrder.getCustomerId(), workOrder.getSalesUserId(), workOrder.getFactoryId(),
                workOrder.getDeviceId(), device == null ? null : device.getDeviceNo(),
                device == null ? null : device.getDeviceName(), workOrder.getMaterialType(),
                workOrder.getProcessType(), workOrder.getFabricWidthMm(), workOrder.getPlannedMeters(),
                workOrder.getPlannedStart(), workOrder.getPlannedEnd(), workOrder.getActualStart(),
                workOrder.getActualEnd(), workOrder.getStatus(), queue == null ? null : queue.getId(),
                queue == null ? null : queue.getQueueSeq(), queue == null ? null : queue.getStatus(),
                queuePosition, queue == null ? null : queue.getEnqueuedAt(),
                queue != null && queue.getJumpRequestId() != null, queue == null ? null : queue.getJumpReason(),
                materialCost, workOrder.getRemark(), workOrder.getCreatedBy(), workOrder.getCreatedAt(),
                workOrder.getUpdatedAt());
    }
}
