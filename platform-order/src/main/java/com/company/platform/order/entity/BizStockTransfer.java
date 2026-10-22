package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 跨厂调拨单实体，对应单表 {@code t_stock_transfer}。
 *
 * <p>状态流转：PENDING 待出库 → IN_TRANSIT 在途（已扣减来源库存）→ COMPLETED
 * 已入库（已增加目标库存）。只有待出库状态可以取消。</p>
 */
@Table("t_stock_transfer")
public class BizStockTransfer {

    /** 调拨单主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 调拨单号 TR-...，全公司唯一。 */
    private String transferNo;
    /** 调出仓库。 */
    private Long fromWarehouseId;
    /** 调出工厂。 */
    private Long fromFactoryId;
    /** 调入仓库。 */
    private Long toWarehouseId;
    /** 调入工厂。 */
    private Long toFactoryId;
    /** 状态：PENDING/IN_TRANSIT/COMPLETED/CANCELLED。 */
    private String status;
    /** 计划调拨日期。 */
    private LocalDate plannedDate;
    /** 出库时间。 */
    private LocalDateTime shippedAt;
    /** 入库时间。 */
    private LocalDateTime receivedAt;
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

    public String getTransferNo() {
        return transferNo;
    }

    public void setTransferNo(String transferNo) {
        this.transferNo = transferNo;
    }

    public Long getFromWarehouseId() {
        return fromWarehouseId;
    }

    public void setFromWarehouseId(Long fromWarehouseId) {
        this.fromWarehouseId = fromWarehouseId;
    }

    public Long getFromFactoryId() {
        return fromFactoryId;
    }

    public void setFromFactoryId(Long fromFactoryId) {
        this.fromFactoryId = fromFactoryId;
    }

    public Long getToWarehouseId() {
        return toWarehouseId;
    }

    public void setToWarehouseId(Long toWarehouseId) {
        this.toWarehouseId = toWarehouseId;
    }

    public Long getToFactoryId() {
        return toFactoryId;
    }

    public void setToFactoryId(Long toFactoryId) {
        this.toFactoryId = toFactoryId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(LocalDate plannedDate) {
        this.plannedDate = plannedDate;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
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
