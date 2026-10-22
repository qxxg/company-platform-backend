package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备台账实体，对应单表 {@code t_device}。
 *
 * <p>设备只归属一个工厂，不绑定客户；材料、门幅、工艺等能力字段是排产匹配
 * 依据。设备编号全公司唯一，台账不做物理删除，停用通过状态 {@code DISABLED}
 * 表达。当前生产工单最多一个，由排产服务在工单开始/完成时维护。</p>
 *
 * <p><strong>表位置：</strong>本表不是分片表，固定落在 {@code ds_0}
 * （company_order0），因此在 ShardingConfig 中以单表规则声明，避免被广播到
 * 两张订单库。</p>
 */
@Table("t_device")
public class BizDevice {

    /** 设备主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 设备编号，全公司唯一。 */
    private String deviceNo;
    /** 设备名称。 */
    private String deviceName;
    /** 设备类型，例如印花机、定型机、染色机。 */
    private String deviceType;
    /** 型号。 */
    private String model;
    /** 所属工厂 ID，必填。 */
    private Long factoryId;
    /** 车间/产线。 */
    private String workshop;
    /** 详细位置。 */
    private String location;
    /** 状态：IDLE/RUNNING/MAINTENANCE/FAULT/DISABLED。 */
    private String status;
    /** 可处理材料，逗号分隔；空字符串表示不限。 */
    private String materialTypes;
    /** 最小门幅（毫米），可为空。 */
    private Integer minWidthMm;
    /** 最大门幅（毫米），可为空。 */
    private Integer maxWidthMm;
    /** 可处理工艺，逗号分隔；空字符串表示不限。 */
    private String processTypes;
    /** 产能：米/小时。 */
    private BigDecimal capacityMetersPerHour;
    /** 换型时间：分钟。 */
    private Integer changeoverMinutes;
    /** 良率百分比 0-100。 */
    private BigDecimal yieldRate;
    /** 操作员资质要求。 */
    private String operatorRequirement;
    /** 当前生产工单 ID；同一时间最多一个。 */
    private Long currentWorkOrderId;
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

    public String getDeviceNo() {
        return deviceNo;
    }

    public void setDeviceNo(String deviceNo) {
        this.deviceNo = deviceNo;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public String getWorkshop() {
        return workshop;
    }

    public void setWorkshop(String workshop) {
        this.workshop = workshop;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMaterialTypes() {
        return materialTypes;
    }

    public void setMaterialTypes(String materialTypes) {
        this.materialTypes = materialTypes;
    }

    public Integer getMinWidthMm() {
        return minWidthMm;
    }

    public void setMinWidthMm(Integer minWidthMm) {
        this.minWidthMm = minWidthMm;
    }

    public Integer getMaxWidthMm() {
        return maxWidthMm;
    }

    public void setMaxWidthMm(Integer maxWidthMm) {
        this.maxWidthMm = maxWidthMm;
    }

    public String getProcessTypes() {
        return processTypes;
    }

    public void setProcessTypes(String processTypes) {
        this.processTypes = processTypes;
    }

    public BigDecimal getCapacityMetersPerHour() {
        return capacityMetersPerHour;
    }

    public void setCapacityMetersPerHour(BigDecimal capacityMetersPerHour) {
        this.capacityMetersPerHour = capacityMetersPerHour;
    }

    public Integer getChangeoverMinutes() {
        return changeoverMinutes;
    }

    public void setChangeoverMinutes(Integer changeoverMinutes) {
        this.changeoverMinutes = changeoverMinutes;
    }

    public BigDecimal getYieldRate() {
        return yieldRate;
    }

    public void setYieldRate(BigDecimal yieldRate) {
        this.yieldRate = yieldRate;
    }

    public String getOperatorRequirement() {
        return operatorRequirement;
    }

    public void setOperatorRequirement(String operatorRequirement) {
        this.operatorRequirement = operatorRequirement;
    }

    public Long getCurrentWorkOrderId() {
        return currentWorkOrderId;
    }

    public void setCurrentWorkOrderId(Long currentWorkOrderId) {
        this.currentWorkOrderId = currentWorkOrderId;
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
