package com.company.platform.order.web;

import jakarta.validation.constraints.Size;

/**
 * 插队审批意见。
 *
 * <p>同意时意见会写入队列调整记录；驳回时作为驳回理由通知申请人。</p>
 */
public record QueueJumpApprovalCommand(
        /** 审批意见，可为空。 */
        @Size(max = 500, message = "remark is too long")
        String remark
) {
}
