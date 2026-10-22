package com.company.platform.order.web;

import com.company.platform.order.entity.BizDevice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备台账只读视图。
 *
 * <p>除台账字段外，还带上当前工单、队列长度和预计空闲时间，方便设备管理员
 * 在列表页直接判断设备负载。当前工单号和订单号由服务层批量补齐，避免前端
 * 再逐台设备查询。</p>
 */
public record DeviceView(
        Long id,
        String deviceNo,
        String deviceName,
        String deviceType,
        String model,
        Long factoryId,
        String workshop,
        String location,
        String status,
        String materialTypes,
        Integer minWidthMm,
        Integer maxWidthMm,
        String processTypes,
        BigDecimal capacityMetersPerHour,
        Integer changeoverMinutes,
        BigDecimal yieldRate,
        String operatorRequirement,
        Long currentWorkOrderId,
        String currentWorkOrderNo,
        Long currentOrderNo,
        Integer queueLength,
        LocalDateTime nextFreeAt,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 从实体转换，并附加当前工单和队列统计。
     *
     * @param device              设备实体
     * @param currentWorkOrderNo  当前生产工单号，可为空
     * @param currentOrderNo      当前生产订单号，可为空
     * @param queueLength         活跃队列长度（等待中 + 生产中）
     * @param nextFreeAt          预计空闲时间，可为空
     */
    public static DeviceView from(BizDevice device, String currentWorkOrderNo, Long currentOrderNo,
                                  Integer queueLength, LocalDateTime nextFreeAt) {
        return new DeviceView(device.getId(), device.getDeviceNo(), device.getDeviceName(), device.getDeviceType(),
                device.getModel(), device.getFactoryId(), device.getWorkshop(), device.getLocation(),
                device.getStatus(), device.getMaterialTypes(), device.getMinWidthMm(), device.getMaxWidthMm(),
                device.getProcessTypes(), device.getCapacityMetersPerHour(), device.getChangeoverMinutes(),
                device.getYieldRate(), device.getOperatorRequirement(),
                // 当前工单号为空时不对外暴露可能残留的历史工单 ID，避免前端误判设备被占用。
                currentWorkOrderNo == null ? null : device.getCurrentWorkOrderId(),
                currentWorkOrderNo, currentOrderNo, queueLength == null ? 0 : queueLength, nextFreeAt,
                device.getRemark(), device.getCreatedAt(), device.getUpdatedAt());
    }
}
