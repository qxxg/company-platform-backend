package com.company.platform.approval.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 审批实例实体，对应表 {@code approval_instance}。
 *
 * <p>一个实例代表一次申请：记录业务类型与业务主键、申请人、金额档位命中的审批
 * 定义，以及整体状态。payload 保存业务参数快照，审批通过后由业务动作执行器
 * 读取并落地实际变更。</p>
 */
@Table("approval_instance")
public class ApprovalInstance {

    /** 实例主键，自增。 */
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 审批单号 AP-...，全公司唯一。 */
    private String instanceNo;
    /** 业务类型。 */
    private String businessType;
    /** 业务主键，例如客户 ID。 */
    private String businessId;
    /** 申请标题。 */
    private String title;
    /** 申请金额：分。 */
    private Long amount;
    /** 工厂，数据范围。 */
    private Long factoryId;
    /** 申请人用户 ID。 */
    private Long applicantUserId;
    /** 申请人姓名快照。 */
    private String applicantName;
    /** 状态：PENDING/APPROVED/REJECTED/CANCELLED。 */
    private String status;
    /** 匹配的审批定义 ID。 */
    private Long definitionId;
    /** 当前步骤。 */
    private Integer currentStep;
    /** 业务参数快照（JSON 文本）。 */
    private String payload;
    /** 审批结论说明。 */
    private String resultNote;
    /** 提交时间。 */
    private LocalDateTime createdAt;
    /** 结束时间。 */
    private LocalDateTime finishedAt;
    /** 最后修改时间。 */
    private LocalDateTime updatedAt;
    /** 版本号，预留乐观锁。 */
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInstanceNo() {
        return instanceNo;
    }

    public void setInstanceNo(String instanceNo) {
        this.instanceNo = instanceNo;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public Long getApplicantUserId() {
        return applicantUserId;
    }

    public void setApplicantUserId(Long applicantUserId) {
        this.applicantUserId = applicantUserId;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(Long definitionId) {
        this.definitionId = definitionId;
    }

    public Integer getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(Integer currentStep) {
        this.currentStep = currentStep;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getResultNote() {
        return resultNote;
    }

    public void setResultNote(String resultNote) {
        this.resultNote = resultNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
