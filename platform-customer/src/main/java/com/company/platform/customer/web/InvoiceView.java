package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizContract;
import com.company.platform.customer.entity.BizCustomer;
import com.company.platform.customer.entity.BizInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 手工发票只读视图。
 *
 * <p>系统单号 {@code FP-xxxxxxxx} 用于列表和关联，发票号码是财务按纸面
 * 发票录入的真实号码。价税合计和税额均以分返回。</p>
 *
 * @param id             发票 ID
 * @param invoiceNo      系统单号，格式 FP-00000001
 * @param customerId     客户 ID
 * @param customerCode   客户编码
 * @param customerName   客户名称
 * @param contractId     合同 ID
 * @param contractNo     合同编号
 * @param orderNo        订单号
 * @param invoiceNumber  发票号码
 * @param invoiceType    发票类型
 * @param invoiceTitle   开票抬头
 * @param taxNo          纳税人识别号
 * @param amount         价税合计，单位分
 * @param taxAmount      税额，单位分
 * @param netAmount      不含税金额 = 价税合计 - 税额，单位分
 * @param taxRate        税率百分数
 * @param invoiceDate    开票日期
 * @param status         REGISTERED/SENT/SIGNED/VOID/REVERSED
 * @param attachmentUrl  附件地址
 * @param remark         备注
 * @param createdBy      登记人
 * @param createdAt      登记时间
 * @param updatedAt      最后修改时间
 */
public record InvoiceView(
        Long id,
        String invoiceNo,
        Long customerId,
        String customerCode,
        String customerName,
        Long contractId,
        String contractNo,
        Long orderNo,
        String invoiceNumber,
        String invoiceType,
        String invoiceTitle,
        String taxNo,
        Long amount,
        Long taxAmount,
        Long netAmount,
        BigDecimal taxRate,
        LocalDate invoiceDate,
        String status,
        String attachmentUrl,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 生成稳定、可读的发票系统单号；数据库仍以自增 ID 关联。 */
    public static String numberOf(Long id) {
        return id == null ? null : "FP-" + String.format(Locale.ROOT, "%08d", id);
    }

    /** 实体 + 客户/合同快照转视图。 */
    public static InvoiceView from(BizInvoice invoice, BizCustomer customer, BizContract contract) {
        long amount = invoice.getAmount() == null ? 0L : invoice.getAmount();
        long tax = invoice.getTaxAmount() == null ? 0L : invoice.getTaxAmount();
        return new InvoiceView(
                invoice.getId(),
                numberOf(invoice.getId()),
                invoice.getCustomerId(),
                customer == null ? null : customer.getCustomerCode(),
                customer == null ? null : customer.getName(),
                invoice.getContractId(),
                contract == null ? null : contract.getContractNo(),
                invoice.getOrderNo(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceType(),
                invoice.getInvoiceTitle(),
                invoice.getTaxNo(),
                amount,
                tax,
                Math.max(0L, amount - tax),
                invoice.getTaxRate(),
                invoice.getInvoiceDate(),
                invoice.getStatus(),
                invoice.getAttachmentUrl(),
                invoice.getRemark(),
                invoice.getCreatedBy(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}
