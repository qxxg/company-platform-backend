package com.company.platform.order.web;

import com.company.platform.order.entity.BizStationMessage;

import java.time.LocalDateTime;

/**
 * 站内消息视图。
 *
 * <p>只暴露当前用户自己的消息；readFlag 转为布尔值，前端无需再判断 0/1。</p>
 */
public record StationMessageView(
        Long id,
        String messageType,
        String title,
        String content,
        String bizType,
        String bizId,
        String linkPath,
        Boolean read,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {

    /** 从实体转换。 */
    public static StationMessageView from(BizStationMessage message) {
        return new StationMessageView(message.getId(), message.getMessageType(), message.getTitle(),
                message.getContent(), message.getBizType(), message.getBizId(), message.getLinkPath(),
                message.getReadFlag() != null && message.getReadFlag() == 1,
                message.getCreatedAt(), message.getReadAt());
    }
}
