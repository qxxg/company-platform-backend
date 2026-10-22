package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备点检保养记录实体，对应单表 {@code t_inspection_record}。
 *
 * <p>操作员按日/周/月登记点检结果；结果异常时可以一键生成维护工单，维护工单
 * 通过 {@code inspectionId} 回溯到点检记录。</p>
 *
 * <p><strong>表位置：</strong>与设备台账、维护工单同库，固定落在 {@code ds_0}
 * （company_order0），在 ShardingConfig 中以单表规则声明。</p>
 */
@Table("t_inspection_record")
public class BizInspectionRecord {

    /** 点检记录主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 设备 ID。 */
    private Long deviceId;
    /** 设备编号快照。 */
    private String deviceNo;
    /** 设备所属工厂。 */
    private Long factoryId;
    /** 点检周期类型：DAILY/WEEKLY/MONTHLY。 */
    private String planType;
    /** 点检日期。 */
    private LocalDate inspectionDate;
    /** 点检结果：NORMAL/ABNORMAL。 */
    private String result;
    /** 点检项数。 */
    private Integer itemCount;
    /** 异常项数。 */
    private Integer abnormalCount;
    /** 点检内容与异常描述。 */
    private String content;
    /** 点检人用户 ID。 */
    private Long handlerUserId;
    /** 点检人姓名快照。 */
    private String handlerName;
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

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public LocalDate getInspectionDate() {
        return inspectionDate;
    }

    public void setInspectionDate(LocalDate inspectionDate) {
        this.inspectionDate = inspectionDate;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public Integer getAbnormalCount() {
        return abnormalCount;
    }

    public void setAbnormalCount(Integer abnormalCount) {
        this.abnormalCount = abnormalCount;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
