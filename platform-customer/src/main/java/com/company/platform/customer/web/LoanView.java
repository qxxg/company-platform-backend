package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizLoan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 借款单只读视图。
 *
 * <p>金额均为分：借款金额、累计已还（含报销冲销）、未还金额。列表接口的
 * repayments 为空列表，详情接口返回完整还款记录。</p>
 *
 * @param id                 借款单 ID
 * @param loanNo             借款单号 JK-...
 * @param borrowerUserId     借款人 ID
 * @param borrowerName       借款人姓名
 * @param amount             借款金额，单位分
 * @param loanDate           借款日期
 * @param purpose            借款用途
 * @param expectedRepayDate  预计还款日期
 * @param factoryId          归属工厂
 * @param department         归属部门
 * @param status             状态编码
 * @param statusName         状态名称
 * @param approvalInstanceId 审批实例 ID
 * @param approvalNote       最近一次审批意见
 * @param disburseMethod     放款方式
 * @param disbursedBy        放款经办人
 * @param disbursedAt        放款时间
 * @param repaidAmount       累计已还，单位分
 * @param outstandingAmount  未还金额，单位分
 * @param createdBy          创建人
 * @param createdAt          创建时间
 * @param updatedAt          最后修改时间
 * @param repayments         还款记录；列表为空
 */
public record LoanView(
        Long id,
        String loanNo,
        Long borrowerUserId,
        String borrowerName,
        Long amount,
        LocalDate loanDate,
        String purpose,
        LocalDate expectedRepayDate,
        Long factoryId,
        String department,
        String status,
        String statusName,
        Long approvalInstanceId,
        String approvalNote,
        String disburseMethod,
        Long disbursedBy,
        LocalDateTime disbursedAt,
        Long repaidAmount,
        Long outstandingAmount,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<LoanRepaymentView> repayments
) {

    /** 状态名称。 */
    public static final Map<String, String> STATUS_NAMES = Map.of(
            "DRAFT", "草稿",
            "PENDING", "审批中",
            "APPROVED", "待放款",
            "DISBURSED", "还款中",
            "REPAID", "已还清",
            "REJECTED", "已驳回",
            "CANCELLED", "已取消"
    );

    /** 状态名称。 */
    public static String statusName(String code) {
        return STATUS_NAMES.getOrDefault(code, code);
    }

    /** 实体转视图。 */
    public static LoanView from(BizLoan loan, List<LoanRepaymentView> repayments) {
        return new LoanView(
                loan.getId(),
                loan.getLoanNo(),
                loan.getBorrowerUserId(),
                loan.getBorrowerName(),
                loan.getAmount(),
                loan.getLoanDate(),
                loan.getPurpose(),
                loan.getExpectedRepayDate(),
                loan.getFactoryId(),
                loan.getDepartment(),
                loan.getStatus(),
                statusName(loan.getStatus()),
                loan.getApprovalInstanceId(),
                loan.getApprovalNote(),
                loan.getDisburseMethod(),
                loan.getDisbursedBy(),
                loan.getDisbursedAt(),
                loan.getRepaidAmount(),
                loan.getOutstandingAmount(),
                loan.getCreatedBy(),
                loan.getCreatedAt(),
                loan.getUpdatedAt(),
                repayments == null ? List.of() : repayments
        );
    }
}
