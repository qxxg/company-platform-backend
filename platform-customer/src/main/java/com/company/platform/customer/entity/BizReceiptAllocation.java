package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 收款核销明细，对应 {@code biz_receipt_allocation}。
 *
 * <p>一条明细表示“某张收款单的多少钱核销到某张应收”。同一收款单对同一应收
 * 只允许一条明细，数据库用唯一键兜底；核销明细不可修改或物理删除，后续红冲
 * 通过反向记录或审批单处理。</p>
 */
@Table("biz_receipt_allocation")
public class BizReceiptAllocation {

    /** 核销明细主键。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 收款单 ID。 */
    private Long receiptId;
    /** 应收 ID。 */
    private Long receivableId;
    /** 本次核销金额，单位：分。 */
    private Long amount;
    /** 核销人。 */
    private Long createdBy;
    /** 核销时间。 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        this.receiptId = receiptId;
    }

    public Long getReceivableId() {
        return receivableId;
    }

    public void setReceivableId(Long receivableId) {
        this.receivableId = receivableId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
