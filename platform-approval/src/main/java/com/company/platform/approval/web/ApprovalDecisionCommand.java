package com.company.platform.approval.web;

import jakarta.validation.constraints.Size;

/**
 * 审批意见命令：同意与驳回共用。
 *
 * <p>驳回必须填写意见，便于申请人知道原因；同意时意见可选。</p>
 */
public record ApprovalDecisionCommand(
        @Size(max = 500, message = "审批意见不能超过 500 字") String comment
) {
}
