package com.company.platform.customer.web;

import jakarta.validation.constraints.Size;

/**
 * 审批结论回调请求。
 *
 * <p>审批中心在同意/驳回后携带审批意见回调业务服务，业务服务据此推进单据
 * 状态。审批意见只做记录，不直接改变金额。</p>
 */
public record ApprovalNoteCommand(
        @Size(max = 500, message = "审批意见不能超过 500 字") String comment
) {
}
