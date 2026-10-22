package com.company.platform.customer.web;

import jakarta.validation.constraints.Size;

/**
 * 借款放款请求。
 *
 * <p>审批通过只代表可放款，财务实际登记后借款才进入还款中。当前不对接银行，
 * 放款方式只做内部记录。</p>
 */
public record LoanDisburseCommand(
        /** 放款方式：CASH/TRANSFER。 */
        @Size(max = 16, message = "放款方式不能超过 16 字") String method
) {
}
