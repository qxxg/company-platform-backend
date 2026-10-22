package com.company.platform.customer.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 新建费用报销单请求。
 *
 * <p>报销人固定取当前登录人，不能由前端指定；金额单位为分，必须大于 0。
 * 工厂、部门、订单、客户都是可选归集维度，不参与审批金额计算。</p>
 */
public record ExpenseClaimCreateCommand(
        /** 费用类型：TRAVEL/ENTERTAINMENT/PURCHASE/RENT_UTILITIES/LOGISTICS/OFFICE/MAINTENANCE/OTHER。 */
        @NotBlank(message = "费用类型不能为空") @Size(max = 32, message = "费用类型不能超过 32 字") String expenseType,
        /** 报销金额，单位分。 */
        @NotNull(message = "报销金额不能为空")
        @Positive(message = "报销金额必须大于 0") Long amount,
        /** 费用发生日期；为空时取今天。 */
        LocalDate expenseDate,
        /** 归属工厂，可空。 */
        Long factoryId,
        /** 归属部门/职能，可空。 */
        @Size(max = 64, message = "部门不能超过 64 字") String department,
        /** 关联订单号，可空，仅用于费用归集。 */
        Long orderNo,
        /** 关联客户 ID，可空，仅用于费用归集。 */
        Long customerId,
        /** 费用说明。 */
        @NotBlank(message = "费用说明不能为空") @Size(max = 500, message = "费用说明不能超过 500 字") String description,
        /** 凭据地址；当前只保存链接。 */
        @Size(max = 500, message = "凭据地址不能超过 500 字") String attachmentUrl
) {
}
