package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizExpenseClaim;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 费用报销单只读视图。
 *
 * <p>金额均为分。列表不一定返回审批轨迹，审批实例号通过审批中心页面查看。</p>
 *
 * @param id                 报销单 ID
 * @param claimNo            报销单号 BX-...
 * @param claimantUserId     报销人 ID
 * @param claimantName       报销人姓名
 * @param expenseType        费用类型编码
 * @param expenseTypeName    费用类型名称
 * @param amount             报销金额，单位分
 * @param expenseDate        费用发生日期
 * @param factoryId          归属工厂
 * @param department         归属部门
 * @param orderNo            关联订单号
 * @param customerId         关联客户 ID
 * @param description        费用说明
 * @param attachmentUrl      凭据地址
 * @param status             状态编码
 * @param statusName         状态名称
 * @param approvalInstanceId 审批实例 ID
 * @param approvalNote       最近一次审批意见
 * @param paidAmount         现金实付金额，单位分
 * @param offsetLoanAmount   冲销借款金额，单位分
 * @param paymentMethod      付款方式
 * @param paymentRemark      付款备注
 * @param paidBy             付款经办人
 * @param paidAt             付款时间
 * @param createdBy          创建人
 * @param createdAt          创建时间
 * @param updatedAt          最后修改时间
 */
public record ExpenseClaimView(
        Long id,
        String claimNo,
        Long claimantUserId,
        String claimantName,
        String expenseType,
        String expenseTypeName,
        Long amount,
        LocalDate expenseDate,
        Long factoryId,
        String department,
        Long orderNo,
        Long customerId,
        String description,
        String attachmentUrl,
        String status,
        String statusName,
        Long approvalInstanceId,
        String approvalNote,
        Long paidAmount,
        Long offsetLoanAmount,
        String paymentMethod,
        String paymentRemark,
        Long paidBy,
        LocalDateTime paidAt,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 费用类型名称；未知编码原样返回，便于排查历史数据。 */
    public static final Map<String, String> TYPE_NAMES = Map.of(
            "TRAVEL", "差旅",
            "ENTERTAINMENT", "业务招待",
            "PURCHASE", "采购",
            "RENT_UTILITIES", "房租水电",
            "LOGISTICS", "物流",
            "OFFICE", "办公",
            "MAINTENANCE", "维修",
            "OTHER", "其他"
    );

    /** 状态名称。 */
    public static final Map<String, String> STATUS_NAMES = Map.of(
            "DRAFT", "草稿",
            "PENDING", "审批中",
            "APPROVED", "待付款",
            "PAID", "已付款",
            "REJECTED", "已驳回",
            "CANCELLED", "已取消"
    );

    /** 费用类型名称。 */
    public static String typeName(String code) {
        return TYPE_NAMES.getOrDefault(code, code);
    }

    /** 状态名称。 */
    public static String statusName(String code) {
        return STATUS_NAMES.getOrDefault(code, code);
    }

    /** 实体转视图。 */
    public static ExpenseClaimView from(BizExpenseClaim claim) {
        return new ExpenseClaimView(
                claim.getId(),
                claim.getClaimNo(),
                claim.getClaimantUserId(),
                claim.getClaimantName(),
                claim.getExpenseType(),
                typeName(claim.getExpenseType()),
                claim.getAmount(),
                claim.getExpenseDate(),
                claim.getFactoryId(),
                claim.getDepartment(),
                claim.getOrderNo(),
                claim.getCustomerId(),
                claim.getDescription(),
                claim.getAttachmentUrl(),
                claim.getStatus(),
                statusName(claim.getStatus()),
                claim.getApprovalInstanceId(),
                claim.getApprovalNote(),
                claim.getPaidAmount(),
                claim.getOffsetLoanAmount(),
                claim.getPaymentMethod(),
                claim.getPaymentRemark(),
                claim.getPaidBy(),
                claim.getPaidAt(),
                claim.getCreatedBy(),
                claim.getCreatedAt(),
                claim.getUpdatedAt()
        );
    }
}
