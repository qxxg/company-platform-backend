package com.company.platform.approval.web;

import com.company.platform.approval.entity.ApprovalDefinition;

import java.time.LocalDateTime;

/**
 * 审批定义视图。
 *
 * <p>金额档位以分为单位展示，前端按元展示但提交仍用分。</p>
 */
public record ApprovalDefinitionView(
        Long id,
        String businessType,
        String name,
        Long amountMin,
        Long amountMax,
        String approverRole,
        Long approverUserId,
        Integer priority,
        String status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 从实体转换视图。 */
    public static ApprovalDefinitionView from(ApprovalDefinition definition) {
        return new ApprovalDefinitionView(definition.getId(), definition.getBusinessType(),
                definition.getName(), definition.getAmountMin(), definition.getAmountMax(),
                definition.getApproverRole(), definition.getApproverUserId(), definition.getPriority(),
                definition.getStatus(), definition.getRemark(), definition.getCreatedAt(),
                definition.getUpdatedAt());
    }
}
