package com.company.platform.customer.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账单明细，对应 {@code biz_statement_line}。
 *
 * <p>明细在生成对账单时一次性写入，之后不可修改：应收发生记 RECEIVABLE，
 * 收款核销记 RECEIPT，开票记 INVOICE。金额单位统一为分。</p>
 */
@Table("biz_statement_line")
public class BizStatementLine {

    /** 明细主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 对账单 ID。 */
    private Long statementId;
    /** 明细类型：RECEIVABLE/RECEIPT/INVOICE。 */
    private String lineType;
    /** 来源业务主键。 */
    private Long refId;
    /** 来源业务单号。 */
    private String refNo;
    /** 业务日期。 */
    private LocalDate bizDate;
    /** 金额，单位：分。 */
    private Long amount;
    /** 明细说明。 */
    private String remark;
    /** 写入时间。 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStatementId() {
        return statementId;
    }

    public void setStatementId(Long statementId) {
        this.statementId = statementId;
    }

    public String getLineType() {
        return lineType;
    }

    public void setLineType(String lineType) {
        this.lineType = lineType;
    }

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }

    public String getRefNo() {
        return refNo;
    }

    public void setRefNo(String refNo) {
        this.refNo = refNo;
    }

    public LocalDate getBizDate() {
        return bizDate;
    }

    public void setBizDate(LocalDate bizDate) {
        this.bizDate = bizDate;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
