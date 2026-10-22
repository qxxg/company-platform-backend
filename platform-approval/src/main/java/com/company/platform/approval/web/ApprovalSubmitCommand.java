package com.company.platform.approval.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 提交审批申请命令。
 *
 * <p>业务类型决定命中的审批定义（金额档位）；payload 保存业务参数快照，例如
 * 信用额度调整的 {@code customerId} 与 {@code creditLimit}，审批通过后由业务
 * 动作执行器读取并落地。</p>
 */
public record ApprovalSubmitCommand(
        @NotBlank(message = "业务类型不能为空") @Size(max = 32, message = "业务类型不能超过 32 字") String businessType,
        @NotBlank(message = "业务主键不能为空") @Size(max = 64, message = "业务主键不能超过 64 字") String businessId,
        @NotBlank(message = "申请标题不能为空") @Size(max = 200, message = "申请标题不能超过 200 字") String title,
        Long amount,
        Long factoryId,
        Map<String, Object> payload
) {
}
