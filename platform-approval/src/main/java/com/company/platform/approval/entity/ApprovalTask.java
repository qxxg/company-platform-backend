package com.company.platform.approval.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 审批任务实体，对应表 {@code approval_task}。
 *
 * <p>任务是审批实例下的待办步骤：审批人可以是指定用户（approverUserId）或角色
 * （approverRole）。当前实例为单步审批，后续扩展多级审批时按 stepNo 排序处理。</p>
 */
@Table("approval_task")
public class ApprovalTask {

    /** 任务主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 审批实例 ID。 */
    private Long instanceId;
    /** 步骤序号。 */
    private Integer stepNo;
    /** 审批角色编码。 */
    private String approverRole;
    /** 指定审批人用户 ID。 */
    private Long approverUserId;
    /** 状态：PENDING/APPROVED/REJECTED/SKIPPED。 */
    private String status;
    /** 审批意见。 */
    private String comment;
    /** 处理人用户 ID。 */
    private Long actedBy;
    /** 处理时间。 */
    private LocalDateTime actedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public Integer getStepNo() {
        return stepNo;
    }

    public void setStepNo(Integer stepNo) {
        this.stepNo = stepNo;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Long getActedBy() {
        return actedBy;
    }

    public void setActedBy(Long actedBy) {
        this.actedBy = actedBy;
    }

    public LocalDateTime getActedAt() {
        return actedAt;
    }

    public void setActedAt(LocalDateTime actedAt) {
        this.actedAt = actedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
