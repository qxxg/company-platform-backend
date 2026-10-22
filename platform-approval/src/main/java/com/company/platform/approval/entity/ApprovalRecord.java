package com.company.platform.approval.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 审批记录实体，对应表 {@code approval_record}。
 *
 * <p>记录只追加不修改，保存提交、同意、驳回与撤销的完整轨迹，便于事后审计。</p>
 */
@Table("approval_record")
public class ApprovalRecord {

    /** 记录主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 审批实例 ID。 */
    private Long instanceId;
    /** 动作：SUBMIT/APPROVE/REJECT/CANCEL/COMMENT。 */
    private String action;
    /** 操作人用户 ID。 */
    private Long operatorUserId;
    /** 操作人姓名快照。 */
    private String operatorName;
    /** 说明。 */
    private String comment;
    /** 发生时间。 */
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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
