package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizReceipt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 收款单只读视图。
 *
 * <p>列表接口不返回核销明细，详情接口返回；可用余额 = 收款金额 - 已核销金额，
 * 未核销部分作为预收/待认领。金额均为分。</p>
 *
 * @param id               收款单 ID
 * @param receiptNo        展示单号，格式 SK-00000001
 * @param customerId       客户 ID
 * @param customerCode     客户编码
 * @param customerName     客户名称
 * @param currency         币种
 * @param amount           收款金额，单位分
 * @param allocatedAmount  已核销金额，单位分
 * @param availableAmount  未核销余额，单位分
 * @param receivedDate     收款日期
 * @param method           收款方式
 * @param bankAccount      收款账户/开户行
 * @param serialNo         银行流水号/票据号
 * @param attachmentUrl    凭证地址
 * @param status           UNALLOCATED/PARTIAL/ALLOCATED/CANCELLED
 * @param remark           备注
 * @param createdBy        登记人
 * @param createdAt        登记时间
 * @param updatedAt        最后修改时间
 * @param allocations      核销明细；列表接口为空列表
 */
public record ReceiptView(
        Long id,
        String receiptNo,
        Long customerId,
        String customerCode,
        String customerName,
        String currency,
        Long amount,
        Long allocatedAmount,
        Long availableAmount,
        LocalDate receivedDate,
        String method,
        String bankAccount,
        String serialNo,
        String attachmentUrl,
        String status,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ReceiptAllocationView> allocations
) {

    /** 生成稳定、可读的收款展示单号；数据库仍以自增 ID 关联。 */
    public static String numberOf(Long id) {
        return id == null ? null : "SK-" + String.format(Locale.ROOT, "%08d", id);
    }

    /** 实体 + 客户快照转视图；核销明细由调用方决定是否填充。 */
    public static ReceiptView from(BizReceipt receipt, com.company.platform.customer.entity.BizCustomer customer,
                                   List<ReceiptAllocationView> allocations) {
        long amount = receipt.getAmount() == null ? 0L : receipt.getAmount();
        long allocated = receipt.getAllocatedAmount() == null ? 0L : receipt.getAllocatedAmount();
        return new ReceiptView(
                receipt.getId(),
                numberOf(receipt.getId()),
                receipt.getCustomerId(),
                customer == null ? null : customer.getCustomerCode(),
                customer == null ? null : customer.getName(),
                receipt.getCurrency(),
                amount,
                allocated,
                Math.max(0L, amount - allocated),
                receipt.getReceivedDate(),
                receipt.getMethod(),
                receipt.getBankAccount(),
                receipt.getSerialNo(),
                receipt.getAttachmentUrl(),
                receipt.getStatus(),
                receipt.getRemark(),
                receipt.getCreatedBy(),
                receipt.getCreatedAt(),
                receipt.getUpdatedAt(),
                allocations == null ? List.of() : allocations
        );
    }
}
