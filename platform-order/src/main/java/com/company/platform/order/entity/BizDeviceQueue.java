package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 设备队列实体，对应 {@code t_device_queue}。
 *
 * <p>队列默认按 {@code queueSeq} 从小到大执行，正常排产时每次追加
 * {@code max(queueSeq) + 1000}，为插队留出调整空间。审批通过后由服务层重排
 * 整个队列并重算预计开始/完成时间，避免出现相同序号导致顺序不确定。</p>
 *
 * <p>一个工单只有一条队列记录，状态从 WAITING → RUNNING → DONE；取消时
 * 变为 CANCELLED。已完成/已取消记录保留，便于追溯等待时长。</p>
 */
@Table("t_device_queue")
public class BizDeviceQueue {

    /** 队列主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 设备 ID。 */
    private Long deviceId;
    /** 工单 ID，一个工单最多一条队列记录。 */
    private Long workOrderId;
    /** 队列序号，越小越靠前。 */
    private Long queueSeq;
    /** 状态：WAITING/RUNNING/DONE/CANCELLED。 */
    private String status;
    /** 进入队列时间，FIFO 的原始依据。 */
    private LocalDateTime enqueuedAt;
    /** 预计开始时间。 */
    private LocalDateTime plannedStart;
    /** 预计完成时间。 */
    private LocalDateTime plannedEnd;
    /** 插队前原队列序号，审计用。 */
    private Long originalSeq;
    /** 生效的插队申请 ID。 */
    private Long jumpRequestId;
    /** 插队原因快照。 */
    private String jumpReason;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId) {
        this.workOrderId = workOrderId;
    }

    public Long getQueueSeq() {
        return queueSeq;
    }

    public void setQueueSeq(Long queueSeq) {
        this.queueSeq = queueSeq;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getEnqueuedAt() {
        return enqueuedAt;
    }

    public void setEnqueuedAt(LocalDateTime enqueuedAt) {
        this.enqueuedAt = enqueuedAt;
    }

    public LocalDateTime getPlannedStart() {
        return plannedStart;
    }

    public void setPlannedStart(LocalDateTime plannedStart) {
        this.plannedStart = plannedStart;
    }

    public LocalDateTime getPlannedEnd() {
        return plannedEnd;
    }

    public void setPlannedEnd(LocalDateTime plannedEnd) {
        this.plannedEnd = plannedEnd;
    }

    public Long getOriginalSeq() {
        return originalSeq;
    }

    public void setOriginalSeq(Long originalSeq) {
        this.originalSeq = originalSeq;
    }

    public Long getJumpRequestId() {
        return jumpRequestId;
    }

    public void setJumpRequestId(Long jumpRequestId) {
        this.jumpRequestId = jumpRequestId;
    }

    public String getJumpReason() {
        return jumpReason;
    }

    public void setJumpReason(String jumpReason) {
        this.jumpReason = jumpReason;
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
