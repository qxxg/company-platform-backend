package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizReceiptAllocation;
import com.company.platform.customer.entity.BizReceivable;

import java.time.LocalDateTime;

/**
 * 收款核销明细视图。
 *
 * <p>用于收款详情展示“这笔钱核销到了哪些应收”。核销明细不可修改，后续
 * 反核销/红冲应生成新的反向记录并保留原记录。</p>
 *
 * @param id           核销明细 ID
 * @param receivableId 应收 ID
 * @param receivableNo 应收展示单号
 * @param contractId   关联合同 ID
 * @param orderNo      关联订单号
 * @param amount       本次核销金额，单位分
 * @param createdAt    核销时间
 */
public record ReceiptAllocationView(
        Long id,
        Long receivableId,
        String receivableNo,
        Long contractId,
        Long orderNo,
        Long amount,
        LocalDateTime createdAt
) {

    /** 核销实体 + 应收实体转视图。 */
    public static ReceiptAllocationView from(BizReceiptAllocation allocation, BizReceivable receivable) {
        return new ReceiptAllocationView(
                allocation.getId(),
                allocation.getReceivableId(),
                ReceivableView.numberOf(allocation.getReceivableId()),
                receivable == null ? null : receivable.getContractId(),
                receivable == null ? null : receivable.getOrderNo(),
                allocation.getAmount(),
                allocation.getCreatedAt()
        );
    }
}
