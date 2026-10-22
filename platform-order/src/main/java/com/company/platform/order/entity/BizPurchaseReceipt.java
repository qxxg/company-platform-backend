package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购入库单实体，对应单表 {@code t_purchase_receipt}。
 *
 * <p>采购入库是库存增加的唯一来源，也是材料成本的唯一来源。确认收货后单据
 * 不可再修改，也没有作废/红冲通道（审批中心上线前）。</p>
 */
@Table("t_purchase_receipt")
public class BizPurchaseReceipt {

    /** 入库单主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 入库单号 PIN-...，全公司唯一。 */
    private String receiptNo;
    /** 收货工厂。 */
    private Long factoryId;
    /** 收货仓库。 */
    private Long warehouseId;
    /** 供应商名称。 */
    private String supplierName;
    /** 收货日期。 */
    private LocalDate receiptDate;
    /** 入库总金额：分。 */
    private Long totalAmount;
    /** 状态：DRAFT/RECEIVED/CANCELLED。 */
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

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public void setReceiptDate(LocalDate receiptDate) {
        this.receiptDate = receiptDate;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
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
