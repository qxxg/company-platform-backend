package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 站内消息实体，对应 {@code t_station_message}。
 *
 * <p>首期只服务于设备队列插队：审批通过后给被顺延工单的业务员发送
 * {@code QUEUE_DISPLACED}，给申请人发送 {@code QUEUE_JUMP_RESULT}。消息只由
 * 当前用户读取和标记已读，不做群发、撤回或物理删除。</p>
 *
 * <p>{@code bizId} 使用字符串而不是 BIGINT，是为了兼容雪花订单号、自增工单号
 * 以及未来审批单号；前端根据 {@code linkPath} 跳转到订单或排产页面。</p>
 */
@Table("t_station_message")
public class BizStationMessage {

    /** 消息主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 接收人用户 ID。 */
    private Long userId;
    /** 消息类型：QUEUE_DISPLACED/QUEUE_JUMP_RESULT/SYSTEM。 */
    private String messageType;
    /** 标题。 */
    private String title;
    /** 内容。 */
    private String content;
    /** 业务类型：WORK_ORDER/ORDER/QUEUE_JUMP。 */
    private String bizType;
    /** 业务主键，字符串兼容雪花号。 */
    private String bizId;
    /** 前端跳转路径。 */
    private String linkPath;
    /** 0 未读 / 1 已读。 */
    private Integer readFlag;
    /** 发送时间。 */
    private LocalDateTime createdAt;
    /** 阅读时间。 */
    private LocalDateTime readAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getLinkPath() {
        return linkPath;
    }

    public void setLinkPath(String linkPath) {
        this.linkPath = linkPath;
    }

    public Integer getReadFlag() {
        return readFlag;
    }

    public void setReadFlag(Integer readFlag) {
        this.readFlag = readFlag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
}
