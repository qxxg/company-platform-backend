package com.company.platform.order.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发起插队申请。
 *
 * <p>只有工单所属业务员可以申请，目标位置从 1 开始。申请只表达诉求，不直接
 * 修改队列；必须等厂长审批通过后才生效。</p>
 */
public record QueueJumpCreateCommand(
        /** 希望插入的队列位置，1 表示队首。 */
        @NotNull(message = "targetPosition is required")
        @Min(value = 1, message = "targetPosition must be at least 1")
        Integer targetPosition,
        /** 插队原因，必填，审批和通知都会引用。 */
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason is too long")
        String reason
) {
}
