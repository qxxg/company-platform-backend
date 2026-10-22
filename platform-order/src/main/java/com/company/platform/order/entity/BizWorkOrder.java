package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工单实体，对应 {@code t_work_order}。
 *
 * <p>一个订单可以按材料、工艺或长度拆成多个工单；每个工单独立选择同一工厂
 * 的设备、独立排队。订单整体完成时间取所有工单的最晚完成时间。工单创建时
 * 可暂不分配设备（待排产），分配后才生成队列记录。</p>
 *
 * <p><strong>冗余字段：</strong>{@code customerId}、{@code salesUserId} 在创建
 * 工单时从订单复制，避免队列通知时再跨分片查询订单。订单号仍是唯一业务关联。</p>
 */
@Table("t_work_order")
public class BizWorkOrder {

    /** 工单主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 工单号 WO-...，全局唯一。 */
    private String workOrderNo;
    /** 订单号，分片键；一个订单可有多个工单。 */
    private Long orderNo;
    /** 客户 ID，下单时冗余。 */
    private Long customerId;
    /** 订单业务员用户 ID，用于站内消息接收人。 */
    private Long salesUserId;
    /** 生产工厂，必须与订单、设备一致。 */
    private Long factoryId;
    /** 分配设备 ID；待排产时为空。 */
    private Long deviceId;
    /** 材料/布料。 */
    private String materialType;
    /** 工艺/染料。 */
    private String processType;
    /** 门幅（毫米）。 */
    private Integer fabricWidthMm;
    /** 计划生产长度（米），保留 2 位小数。 */
    private BigDecimal plannedMeters;
    /** 计划开始时间。 */
    private LocalDateTime plannedStart;
    /** 计划完成时间。 */
    private LocalDateTime plannedEnd;
    /** 实际开始时间。 */
    private LocalDateTime actualStart;
    /** 实际完成时间。 */
    private LocalDateTime actualEnd;
    /** 状态：PENDING/QUEUED/RUNNING/COMPLETED/CANCELLED。 */
    private String status;
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

    public String getWorkOrderNo() {
        return workOrderNo;
    }

    public void setWorkOrderNo(String workOrderNo) {
        this.workOrderNo = workOrderNo;
    }

    public Long getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Long orderNo) {
        this.orderNo = orderNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getSalesUserId() {
        return salesUserId;
    }

    public void setSalesUserId(Long salesUserId) {
        this.salesUserId = salesUserId;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getMaterialType() {
        return materialType;
    }

    public void setMaterialType(String materialType) {
        this.materialType = materialType;
    }

    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    public Integer getFabricWidthMm() {
        return fabricWidthMm;
    }

    public void setFabricWidthMm(Integer fabricWidthMm) {
        this.fabricWidthMm = fabricWidthMm;
    }

    public BigDecimal getPlannedMeters() {
        return plannedMeters;
    }

    public void setPlannedMeters(BigDecimal plannedMeters) {
        this.plannedMeters = plannedMeters;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
