package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 设备维护工单实体，对应单表 {@code t_maintenance_order}。
 *
 * <p>维护工单是设备停机保养的唯一业务凭据：开始维护时设备转为保养或故障
 * 状态，等待队列保留并通知受影响业务员；完成后设备恢复空闲并重算队列计划
 * 时间。</p>
 *
 * <p><strong>成本口径：</strong>维护费用全部由公司承担，只做成本记录，不向
 * 客户收取，也不计算设备使用费；金额以分存储，与财务模块一致。</p>
 *
 * <p><strong>表位置：</strong>与设备台账、排产表同库，固定落在 {@code ds_0}
 * （company_order0），在 ShardingConfig 中以单表规则声明。</p>
 */
@Table("t_maintenance_order")
public class BizMaintenanceOrder {

    /** 维护工单主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 维护工单号 MT-...，全公司唯一。 */
    private String maintenanceNo;
    /** 设备 ID。 */
    private Long deviceId;
    /** 设备编号快照，设备改名不影响历史单据。 */
    private String deviceNo;
    /** 设备所属工厂，维护数据范围依据。 */
    private Long factoryId;
    /** 维护类型：PREVENTIVE/REPAIR/INSPECTION。 */
    private String maintenanceType;
    /** 维护主题。 */
    private String title;
    /** 问题描述与处理方案。 */
    private String description;
    /** 优先级：LOW/NORMAL/HIGH/URGENT。 */
    private String priority;
    /** 状态：PENDING/IN_PROGRESS/COMPLETED/CANCELLED。 */
    private String status;
    /** 计划开始时间。 */
    private LocalDateTime plannedStart;
    /** 计划结束时间。 */
    private LocalDateTime plannedEnd;
    /** 实际开始时间。 */
    private LocalDateTime actualStart;
    /** 实际结束时间。 */
    private LocalDateTime actualEnd;
    /** 停机时长：分钟。 */
    private Integer downtimeMinutes;
    /** 维护成本：分，公司承担。 */
    private Long costAmount;
    /** 成本说明（外协、备件、人工等）。 */
    private String costNote;
    /** 执行人用户 ID。 */
    private Long handlerUserId;
    /** 执行人姓名快照。 */
    private String handlerName;
    /** 来源点检记录 ID，可为空。 */
    private Long inspectionId;
    /** 备注。 */
    private String remark;
    /** 创建人。 */
    private Long createdBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后修改人。 */
    private Long updatedBy;
    /** 最后修改时间。 */
    private LocalDateTime updatedAt;
    /** 版本号，预留乐观锁。 */
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMaintenanceNo() {
        return maintenanceNo;
    }

    public void setMaintenanceNo(String maintenanceNo) {
        this.maintenanceNo = maintenanceNo;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public void setDeviceNo(String deviceNo) {
        this.deviceNo = deviceNo;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public String getMaintenanceType() {
        return maintenanceType;
    }

    public void setMaintenanceType(String maintenanceType) {
        this.maintenanceType = maintenanceType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getActualStart() {
        return actualStart;
    }

    public void setActualStart(LocalDateTime actualStart) {
        this.actualStart = actualStart;
    }

    public LocalDateTime getActualEnd() {
        return actualEnd;
    }

    public void setActualEnd(LocalDateTime actualEnd) {
        this.actualEnd = actualEnd;
    }

    public Integer getDowntimeMinutes() {
        return downtimeMinutes;
    }

    public void setDowntimeMinutes(Integer downtimeMinutes) {
        this.downtimeMinutes = downtimeMinutes;
    }

    public Long getCostAmount() {
        return costAmount;
    }

    public void setCostAmount(Long costAmount) {
        this.costAmount = costAmount;
    }

    public String getCostNote() {
        return costNote;
    }

    public void setCostNote(String costNote) {
        this.costNote = costNote;
    }

    public Long getHandlerUserId() {
        return handlerUserId;
    }

    public void setHandlerUserId(Long handlerUserId) {
        this.handlerUserId = handlerUserId;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }

    public Long getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(Long inspectionId) {
        this.inspectionId = inspectionId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
