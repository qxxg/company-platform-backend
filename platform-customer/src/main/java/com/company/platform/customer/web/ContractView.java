package com.company.platform.customer.web;

import com.company.platform.customer.entity.BizContract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同只读视图。
 *
 * <p>合同金额单位分；剩余额度需要结合订单库计算，因此不在本视图伪造。
 * 前端的合同执行页会分别展示合同额、已下单额和剩余额。</p>
 */
public record ContractView(
        Long id,
        String contractNo,
        Long customerId,
        String contractType,
        LocalDate startDate,
        LocalDate endDate,
        Long contractAmount,
        String currency,
        String settlementMethod,
        Integer paymentDays,
        BigDecimal taxRate,
        String priceTerms,
        String qualityTerms,
        String status,
        Long entryUserId,
        String entryUserName,
        LocalDateTime entryTime,
        String attachmentUrl,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 实体转视图。 */
    public static ContractView from(BizContract contract) {
        return new ContractView(
                contract.getId(), contract.getContractNo(), contract.getCustomerId(), contract.getContractType(),
                contract.getStartDate(), contract.getEndDate(), contract.getContractAmount(), contract.getCurrency(),
                contract.getSettlementMethod(), contract.getPaymentDays(), contract.getTaxRate(),
                contract.getPriceTerms(), contract.getQualityTerms(), contract.getStatus(),
                contract.getEntryUserId(), contract.getEntryUserName(), contract.getEntryTime(),
                contract.getAttachmentUrl(), contract.getRemark(), contract.getCreatedAt(), contract.getUpdatedAt());
    }
}
