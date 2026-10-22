package com.company.platform.customer.web;

import jakarta.validation.constraints.Size;

/**
 * 客户确认对账单请求。
 *
 * <p>客户确认时如存在差异，必须把差异原因写入 {@code differenceNote}，
 * 便于后续按订单/收款/发票追溯；确认后对账单归档，不再允许修改。</p>
 */
public record StatementConfirmCommand(
        /** 差异说明，可为空。 */
        @Size(max = 500, message = "差异说明不能超过 500 个字符") String differenceNote
) {
}
