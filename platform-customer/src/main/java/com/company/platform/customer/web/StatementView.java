package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizCustomer;
import com.company.platform.customer.entity.BizStatement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 客户对账单视图。
 *
 * <p>期末余额 = 期初 + 本期应收 - 本期核销收款；本期开票金额只作为参考，
 * 因为开票不等于应收发生。列表接口不返回明细，详情接口返回。</p>
 *
 * @param id               对账单 ID
 * @param statementNo      展示单号，格式 DZ-00000001
 * @param customerId       客户 ID
 * @param customerCode     客户编码
 * @param customerName     客户名称
 * @param periodStart      期间开始日
 * @param periodEnd        期间结束日
 * @param openingAmount    期初应收余额，单位分
 * @param receivableAmount 本期新增应收，单位分
 * @param receivedAmount   本期核销收款，单位分
 * @param invoiceAmount    本期开票金额，单位分
 * @param closingAmount    期末应收余额，单位分
 * @param status           GENERATED/CONFIRMED/CANCELLED
 * @param differenceNote   差异说明
 * @param confirmedBy      确认人
 * @param confirmedAt      确认时间
 * @param remark           备注
 * @param createdAt        生成时间
 * @param lines            明细；列表接口为空列表
 */
public record StatementView(
        Long id,
        String statementNo,
        Long customerId,
        String customerCode,
        String customerName,
        LocalDate periodStart,
        LocalDate periodEnd,
        Long openingAmount,
        Long receivableAmount,
        Long receivedAmount,
        Long invoiceAmount,
        Long closingAmount,
        String status,
        String differenceNote,
        Long confirmedBy,
        LocalDateTime confirmedAt,
        String remark,
        LocalDateTime createdAt,
        List<StatementLineView> lines
) {

    /** 生成稳定、可读的对账单展示单号；数据库仍以自增 ID 关联。 */
    public static String numberOf(Long id) {
        return id == null ? null : "DZ-" + String.format(Locale.ROOT, "%08d", id);
    }

    /** 实体 + 客户快照转视图；明细由调用方决定是否填充。 */
    public static StatementView from(BizStatement statement, BizCustomer customer,
                                     List<StatementLineView> lines) {
        return new StatementView(
                statement.getId(),
                numberOf(statement.getId()),
                statement.getCustomerId(),
                customer == null ? null : customer.getCustomerCode(),
                customer == null ? null : customer.getName(),
                statement.getPeriodStart(),
                statement.getPeriodEnd(),
                zero(statement.getOpeningAmount()),
                zero(statement.getReceivableAmount()),
                zero(statement.getReceivedAmount()),
                zero(statement.getInvoiceAmount()),
                zero(statement.getClosingAmount()),
                statement.getStatus(),
                statement.getDifferenceNote(),
                statement.getConfirmedBy(),
                statement.getConfirmedAt(),
                statement.getRemark(),
                statement.getCreatedAt(),
                lines == null ? List.of() : lines
        );
    }

    /** 空金额按 0 处理。 */
    private static long zero(Long value) {
        return value == null ? 0L : value;
    }
}
