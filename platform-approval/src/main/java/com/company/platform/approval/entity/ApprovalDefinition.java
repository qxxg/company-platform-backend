package com.company.platform.approval.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 审批定义实体，对应表 {@code approval_definition}。
 *
 * <p>审批定义按“业务类型 + 金额档位”匹配审批人：可以指定审批角色，也可以指定
 * 具体审批人；优先级数字越小越先匹配。金额以分存储，与财务模块口径一致。</p>
 */
@Table("approval_definition")
public class ApprovalDefinition {

    /** 定义主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 业务类型：CREDIT_LIMIT/INVOICE_VOID/EXPENSE/LOAN/QUEUE_JUMP/WAREHOUSE。 */
    private String businessType;
    /** 流程名称。 */
    private String name;
    /** 金额下限：分，含。 */
    private Long amountMin;
    /** 金额上限：分，不含；为空表示不封顶。 */
    private Long amountMax;
    /** 审批角色编码。 */
    private String approverRole;
    /** 指定审批人用户 ID。 */
    private Long approverUserId;
    /** 匹配优先级，数字小的优先。 */
    private Integer priority;
    /** 状态：ACTIVE/DISABLED。 */
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

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getAmountMin() {
        return amountMin;
    }

    public void setAmountMin(Long amountMin) {
        this.amountMin = amountMin;
    }

    public Long getAmountMax() {
        return amountMax;
    }

    public void setAmountMax(Long amountMax) {
        this.amountMax = amountMax;
    }

    public String getApproverRole() {
        return approverRole;
    }

    public void setApproverRole(String approverRole) {
        this.approverRole = approverRole;
    }

    public Long getApproverUserId() {
        return approverUserId;
    }

    public void setApproverUserId(Long approverUserId) {
        this.approverUserId = approverUserId;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
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
