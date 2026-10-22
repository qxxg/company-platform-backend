package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizLoanRepayment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 借款还款记录视图。
 *
 * @param id              还款记录 ID
 * @param repaymentNo     还款单号 HK-...
 * @param amount          本次还款金额，单位分
 * @param repaymentDate   还款日期
 * @param method          方式 CASH/TRANSFER/OFFSET
 * @param methodName      方式名称
 * @param expenseClaimId  冲销的报销单 ID
 * @param expenseClaimNo  冲销的报销单号
 * @param remark          备注
 * @param createdBy       登记人
 * @param createdAt       登记时间
 */
public record LoanRepaymentView(
        Long id,
        String repaymentNo,
        Long amount,
        LocalDate repaymentDate,
        String method,
        String methodName,
        Long expenseClaimId,
        String expenseClaimNo,
        String remark,
        Long createdBy,
        LocalDateTime createdAt
) {

    /** 还款方式名称。 */
    public static final Map<String, String> METHOD_NAMES = Map.of(
            "CASH", "现金",
            "TRANSFER", "转账",
            "OFFSET", "报销冲销"
    );

    /** 实体转视图。 */
    public static LoanRepaymentView from(BizLoanRepayment repayment) {
        return new LoanRepaymentView(
                repayment.getId(),
                repayment.getRepaymentNo(),
                repayment.getAmount(),
                repayment.getRepaymentDate(),
                repayment.getMethod(),
                METHOD_NAMES.getOrDefault(repayment.getMethod(), repayment.getMethod()),
                repayment.getExpenseClaimId(),
                repayment.getExpenseClaimNo(),
                repayment.getRemark(),
                repayment.getCreatedBy(),
                repayment.getCreatedAt()
        );
    }
}
