package com.company.platform.order.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 订单实体，对应逻辑表 {@code t_order}。
 *
 * <p>逻辑表在 ShardingSphere 中映射到 2 个库、每库 4 张物理表：
 * 库由 {@code order_no % 2} 决定，表由 {@code (order_no >> 1) % 4} 决定。
 * 因此 orderNo 既不是数据库自增，也不能在业务中重新生成。</p>
 */
@Table("t_order")
public class BizOrder {

    /** 雪花订单号，主键兼分片键；由应用生成。 */
    @Id(keyType = KeyType.None)
    private Long orderNo;
    /** 客户编号；客户模块落地后改为外键关联。 */
    private Long customerId;
    /** 已录入合同 ID；所有订单必须有合同，0 只存在于旧数据。 */
    private Long contractId;
    /** 生产工厂编号，订单不允许跨工厂。 */
    private Long factoryId;
    /** 订单状态：CREATED/QUEUED/IN_PRODUCTION/COMPLETED/CANCELLED。 */
    private String orderStatus;
    /** 订单总额，单位：分，整数存储并直接截断。 */
    private Long totalAmount;
    /** 币种，默认 CNY。 */
    private String currency;
    /** 订单备注，最大 500 字符。 */
    private String remark;
    /** 创建人用户 ID，来自 JWT。 */
    private Long createdBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

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

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
