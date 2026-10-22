package com.company.platform.customer.web;

import java.time.LocalDate;

/**
 * 对账单明细视图。
 *
 * @param id        明细 ID
 * @param lineType  RECEIVABLE/RECEIPT/INVOICE
 * @param refId     来源业务主键
 * @param refNo     来源业务单号
 * @param bizDate   业务日期
 * @param amount    金额，单位分
 * @param remark    明细说明
 */
public record StatementLineView(
        Long id,
        String lineType,
        Long refId,
        String refNo,
        LocalDate bizDate,
        Long amount,
        String remark
) {

    /** 实体转视图。 */
    public static StatementLineView from(com.company.platform.customer.entity.BizStatementLine line) {
        return new StatementLineView(line.getId(), line.getLineType(), line.getRefId(), line.getRefNo(),
                line.getBizDate(), line.getAmount(), line.getRemark());
    }
}
