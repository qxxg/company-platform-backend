package com.company.platform.order.web;

import com.company.platform.order.entity.BizDeviceQueue;
import com.company.platform.order.entity.BizWorkOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备队列中的一条记录。
 *
 * <p>看板按设备分组展示，每条记录包含队列位置、工单号、订单号、业务员和预计
 * 时间。业务员 ID 只用于前端判断是否显示“申请插队”，不暴露其他客户信息。</p>
 */
public record QueueEntryView(
        Long queueId,
        Integer position,
        Long queueSeq,
        String queueStatus,
        LocalDateTime enqueuedAt,
        Long workOrderId,
        String workOrderNo,
        Long orderNo,
        Long customerId,
        Long salesUserId,
        String materialType,
        String processType,
        Integer fabricWidthMm,
        BigDecimal plannedMeters,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        String workOrderStatus,
        Boolean jumped,
        String jumpReason,
        Long jumpRequestId
) {

    /** 从队列和工单组合视图。 */
    public static QueueEntryView from(BizDeviceQueue queue, BizWorkOrder workOrder, int position) {
        return new QueueEntryView(queue.getId(), position, queue.getQueueSeq(), queue.getStatus(),
                queue.getEnqueuedAt(), workOrder.getId(), workOrder.getWorkOrderNo(), workOrder.getOrderNo(),
                workOrder.getCustomerId(), workOrder.getSalesUserId(), workOrder.getMaterialType(),
                workOrder.getProcessType(), workOrder.getFabricWidthMm(), workOrder.getPlannedMeters(),
                queue.getPlannedStart(), queue.getPlannedEnd(), workOrder.getStatus(),
                queue.getJumpRequestId() != null, queue.getJumpReason(), queue.getJumpRequestId());
    }
}
