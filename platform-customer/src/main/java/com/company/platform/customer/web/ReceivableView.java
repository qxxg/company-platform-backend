package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizContract;
import com.company.platform.customer.entity.BizCustomer;
import com.company.platform.customer.entity.BizReceivable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * 应收只读视图。
 *
 * <p>逾期和账龄不落库，按“未收余额 + 到期日 + 当前日期”实时计算，避免
 * 批量任务未跑导致筛选结果过期。金额均为分，前端只负责格式化展示。</p>
 *
 * @param id                应收 ID
 * @param receivableNo      展示单号，格式 AR-00000001
 * @param customerId        客户 ID
 * @param customerCode      客户编码
 * @param customerName      客户名称
 * @param contractId        合同 ID，可为空
 * @param contractNo        合同编号，可为空
 * @param orderNo           订单号，可为空
 * @param amount            应收金额，单位分
 * @param currency          币种
 * @param receivedAmount    已收金额，单位分
 * @param outstandingAmount 未收金额，单位分；已取消时为 0
 * @param dueDate           到期日
 * @param status            UNPAID/PARTIAL/PAID/CANCELLED
 * @param overdue           是否逾期
 * @param overdueDays       逾期天数，未逾期为 0
 * @param agingBucket       账龄区间
 * @param sourceType        来源
 * @param businessOwnerId   业务员用户 ID
 * @param businessOwnerName 业务员姓名快照
 * @param remark            备注
 * @param createdAt         创建时间
 * @param updatedAt         最后修改时间
 */
public record ReceivableView(
        Long id,
        String receivableNo,
        Long customerId,
        String customerCode,
        String customerName,
        Long contractId,
        String contractNo,
        Long orderNo,
        Long amount,
        String currency,
        Long receivedAmount,
        Long outstandingAmount,
        LocalDate dueDate,
        String status,
        boolean overdue,
        int overdueDays,
        String agingBucket,
        String sourceType,
        Long businessOwnerId,
        String businessOwnerName,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 生成稳定、可读的应收展示单号；数据库仍以自增 ID 关联。 */
    public static String numberOf(Long id) {
        return id == null ? null : "AR-" + String.format(Locale.ROOT, "%08d", id);
    }

    /** 实体 + 客户/合同快照转视图；金额和逾期在这里统一计算。 */
    public static ReceivableView from(BizReceivable receivable, BizCustomer customer, BizContract contract) {
        long amount = receivable.getAmount() == null ? 0L : receivable.getAmount();
        long received = receivable.getReceivedAmount() == null ? 0L : receivable.getReceivedAmount();
        boolean cancelled = "CANCELLED".equals(receivable.getStatus());
        long outstanding = cancelled ? 0L : Math.max(0L, amount - received);
        int overdueDays = 0;
        if (outstanding > 0 && receivable.getDueDate() != null) {
            long days = ChronoUnit.DAYS.between(receivable.getDueDate(), LocalDate.now());
            overdueDays = days > 0 ? (int) Math.min(Integer.MAX_VALUE, days) : 0;
        }
        String agingBucket = agingBucket(outstanding, receivable.getDueDate(), receivable.getStatus());
        return new ReceivableView(
                receivable.getId(), numberOf(receivable.getId()),
                receivable.getCustomerId(),
                customer == null ? null : customer.getCustomerCode(),
                customer == null ? null : customer.getName(),
                receivable.getContractId(),
                contract == null ? null : contract.getContractNo(),
                receivable.getOrderNo(),
                amount,
                receivable.getCurrency(),
                received,
                outstanding,
                receivable.getDueDate(),
                receivable.getStatus(),
                overdueDays > 0,
                overdueDays,
                agingBucket,
                receivable.getSourceType(),
                receivable.getBusinessOwnerId(),
                customer == null ? null : customer.getOwnerName(),
                receivable.getRemark(),
                receivable.getCreatedAt(),
                receivable.getUpdatedAt()
        );
    }

    /** 账龄区间：已取消/已结清优先，其次按逾期天数分档。 */
    private static String agingBucket(long outstanding, LocalDate dueDate, String status) {
        if ("CANCELLED".equals(status)) {
            return "已取消";
        }
        if (outstanding <= 0) {
            return "已结清";
        }
        if (dueDate == null) {
            return "无到期日";
        }
        long days = ChronoUnit.DAYS.between(dueDate, LocalDate.now());
        if (days <= 0) {
            return "未到期";
        }
        if (days <= 30) {
            return "逾期 1-30 天";
        }
        if (days <= 60) {
            return "逾期 31-60 天";
        }
        if (days <= 90) {
            return "逾期 61-90 天";
        }
        return "逾期 90 天以上";
    }
}
