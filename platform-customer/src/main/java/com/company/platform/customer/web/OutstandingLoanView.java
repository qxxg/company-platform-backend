package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizLoan;

import java.time.LocalDate;
import java.util.List;

/**
 * 可选择冲销的未还借款。
 *
 * <p>财务付款时按报销人查询，仅返回已放款且未还金额大于 0 的借款单。</p>
 */
public record OutstandingLoanView(
        Long id,
        String loanNo,
        Long amount,
        Long outstandingAmount,
        LocalDate loanDate,
        String purpose
) {

    /** 实体转视图。 */
    public static OutstandingLoanView from(BizLoan loan) {
        return new OutstandingLoanView(
                loan.getId(),
                loan.getLoanNo(),
                loan.getAmount(),
                loan.getOutstandingAmount(),
                loan.getLoanDate(),
                loan.getPurpose()
        );
    }

    /** 批量转换。 */
    public static List<OutstandingLoanView> from(List<BizLoan> loans) {
        return loans.stream().map(OutstandingLoanView::from).toList();
    }
}
