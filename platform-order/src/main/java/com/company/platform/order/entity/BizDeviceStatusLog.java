package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 设备状态变更日志，对应 {@code t_device_status_log}。
 *
 * <p>设备台账要求状态变化全程留痕，因此每次人工改状态、工单开始/完成导致的
 * 状态变化都追加一行日志，不修改、不删除历史。日志只记录状态和原因，不承担
 * 审计系统的职责；操作人来自 JWT。</p>
 */
@Table("t_device_status_log")
public class BizDeviceStatusLog {

    /** 日志主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 设备 ID。 */
    private Long deviceId;
    /** 变更前状态，首次登记时为空。 */
    private String fromStatus;
    /** 变更后状态。 */
    private String toStatus;
    /** 变更原因。 */
    private String reason;
    /** 关联工单 ID，可为空。 */
    private Long workOrderId;
    /** 操作人用户 ID。 */
    private Long changedBy;
    /** 变更时间。 */
    private LocalDateTime changedAt;

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

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId) {
        this.workOrderId = workOrderId;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Long changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
