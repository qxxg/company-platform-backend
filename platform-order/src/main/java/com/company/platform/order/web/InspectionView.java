package com.company.platform.order.web;

import com.company.platform.order.entity.BizInspectionRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备点检记录视图。
 *
 * <p>补齐设备名称与点检人姓名快照，便于列表直接展示，不必再回查台账。</p>
 */
public record InspectionView(
        Long id,
        Long deviceId,
        String deviceNo,
        String deviceName,
        Long factoryId,
        String planType,
        LocalDate inspectionDate,
        String result,
        Integer itemCount,
        Integer abnormalCount,
        String content,
        Long handlerUserId,
        String handlerName,
        Long createdBy,
        LocalDateTime createdAt
) {

    /**
     * 从实体转换视图。
     *
     * @param record     点检记录实体
     * @param deviceName 设备名称快照，可为空
     */
    public static InspectionView from(BizInspectionRecord record, String deviceName) {
        return new InspectionView(record.getId(), record.getDeviceId(), record.getDeviceNo(), deviceName,
                record.getFactoryId(), record.getPlanType(), record.getInspectionDate(), record.getResult(),
                record.getItemCount(), record.getAbnormalCount(), record.getContent(),
                record.getHandlerUserId(), record.getHandlerName(), record.getCreatedBy(), record.getCreatedAt());
    }
}
