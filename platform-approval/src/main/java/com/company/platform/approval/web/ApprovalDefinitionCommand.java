package com.company.platform.approval.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 审批定义维护命令。
 *
 * <p>金额档位以分为单位：下限含、上限不含；上限为空表示不封顶。一个业务类型
 * 可以配置多档，按优先级从小到大匹配。</p>
 */
public record ApprovalDefinitionCommand(
        @NotBlank(message = "业务类型不能为空") @Size(max = 32, message = "业务类型不能超过 32 字") String businessType,
        @NotBlank(message = "流程名称不能为空") @Size(max = 128, message = "流程名称不能超过 128 字") String name,
        @NotNull(message = "金额下限不能为空") @Min(value = 0, message = "金额下限不能为负数") Long amountMin,
        @Min(value = 0, message = "金额上限不能为负数") Long amountMax,
        @Size(max = 32, message = "审批角色不能超过 32 字") String approverRole,
        Long approverUserId,
        @NotNull(message = "优先级不能为空") @Min(value = 1, message = "优先级不能小于 1") Integer priority,
        @Size(max = 500, message = "备注不能超过 500 字") String remark
) {
}
