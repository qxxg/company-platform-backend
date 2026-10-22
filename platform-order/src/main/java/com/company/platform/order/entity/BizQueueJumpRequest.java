package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 设备队列插队申请，对应 {@code t_queue_jump_request}。
 *
 * <p>V1.4 要求急单插队必须“销售申请 → 工厂管理审批”，审批通过后立即调整
 * 队列、记录原因，并给被顺延工单的业务员发送站内消息，不做经济补偿。审批
 * 中心尚未上线，本阶段由设备队列模块提供独立申请/审批接口；后续迁移到统一
 * 审批中心时保留本表作为业务单据。</p>
 */
@Table("t_queue_jump_request")
public class BizQueueJumpRequest {

    /** 申请主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 申请单号 QJ-...，全局唯一。 */
    private String requestNo;
    /** 申请插队的工单 ID。 */
    private Long workOrderId;
    /** 目标设备 ID。 */
    private Long deviceId;
    /** 工厂 ID，用于审批数据范围。 */
    private Long factoryId;
    /** 申请人（销售）用户 ID。 */
    private Long requestedBy;
    /** 希望插入的队列位置，1 表示队首。 */
    private Integer targetPosition;
    /** 插队原因，必填。 */
    private String reason;
    /** 状态：PENDING/APPROVED/REJECTED/CANCELLED。 */
    private String status;
    /** 审批人用户 ID。 */
    private Long approvedBy;
    /** 审批时间。 */
    private LocalDateTime approvedAt;
    /** 审批意见。 */
    private String approvalRemark;
    /** 被顺延工单数量。 */
    private Integer displacedCount;
    /** 申请时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public void setRequestNo(String requestNo) {
        this.requestNo = requestNo;
    }

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId) {
        this.workOrderId = workOrderId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Integer getTargetPosition() {
        return targetPosition;
    }

    public void setTargetPosition(Integer targetPosition) {
        this.targetPosition = targetPosition;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getApprovalRemark() {
        return approvalRemark;
    }

    public void setApprovalRemark(String approvalRemark) {
        this.approvalRemark = approvalRemark;
    }

    public Integer getDisplacedCount() {
        return displacedCount;
    }

    public void setDisplacedCount(Integer displacedCount) {
        this.displacedCount = displacedCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
