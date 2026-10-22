package com.company.platform.order.web;

import com.company.platform.order.entity.BizDevice;
import com.company.platform.order.entity.BizQueueJumpRequest;
import com.company.platform.order.entity.BizWorkOrder;

import java.time.LocalDateTime;

/**
 * 插队申请视图。
 *
 * <p>除申请字段外，带出工单号、订单号、设备编号和当前队列位置，审批人可以
 * 在不跳转页面的情况下判断影响范围。</p>
 */
public record JumpRequestView(
        Long id,
        String requestNo,
        Long workOrderId,
        String workOrderNo,
        Long orderNo,
        Long deviceId,
        String deviceNo,
        Long factoryId,
        Long requestedBy,
        Integer targetPosition,
        String reason,
        String status,
        Long approvedBy,
        LocalDateTime approvedAt,
        String approvalRemark,
        Integer displacedCount,
        Integer currentPosition,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 从申请、工单和设备组合视图；currentPosition 由服务层实时计算。 */
    public static JumpRequestView from(BizQueueJumpRequest request, BizWorkOrder workOrder,
                                       BizDevice device, Integer currentPosition) {
        return new JumpRequestView(request.getId(), request.getRequestNo(), request.getWorkOrderId(),
                workOrder == null ? null : workOrder.getWorkOrderNo(),
                workOrder == null ? null : workOrder.getOrderNo(), request.getDeviceId(),
                device == null ? null : device.getDeviceNo(), request.getFactoryId(), request.getRequestedBy(),
                request.getTargetPosition(), request.getReason(), request.getStatus(), request.getApprovedBy(),
                request.getApprovedAt(), request.getApprovalRemark(), request.getDisplacedCount(),
                currentPosition, request.getCreatedAt(), request.getUpdatedAt());
    }
}
