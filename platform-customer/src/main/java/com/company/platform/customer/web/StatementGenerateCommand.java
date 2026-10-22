package com.company.platform.customer.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 生成客户对账单请求。
 *
 * <p>对账期间为闭区间；期末不能晚于今天，避免把未来业务计入对账。
 * 同一客户同一期间允许重复生成，每次生成都会固化一份新快照用于追溯。</p>
 */
public record StatementGenerateCommand(
        /** 客户 ID。 */
        @NotNull(message = "客户不能为空") Long customerId,
        /** 期间开始日。 */
        @NotNull(message = "期间开始日不能为空") LocalDate periodStart,
        /** 期间结束日。 */
        @NotNull(message = "期间结束日不能为空") LocalDate periodEnd,
        /** 备注。 */
        @Size(max = 500, message = "备注不能超过 500 个字符") String remark
) {
}
