package com.company.platform.approval.web;

import com.company.platform.approval.entity.ApprovalInstance;
import com.company.platform.approval.entity.ApprovalTask;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批单视图：实例 + 待办任务 + 审批轨迹。
 */
public record ApprovalView(
        Long id,
        String instanceNo,
        String businessType,
        String businessId,
        String title,
        Long amount,
        Long factoryId,
        Long applicantUserId,
        String applicantName,
        String status,
        Long definitionId,
        String definitionName,
        Integer currentStep,
        String payload,
        String resultNote,
        List<Task> tasks,
        List<Record> records,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        LocalDateTime updatedAt
) {

    /**
     * 审批任务视图。
     *
     * @param id           任务 ID
     * @param stepNo       步骤序号
     * @param approverRole 审批角色
     * @param approverUserId 指定审批人
     * @param status       任务状态
     * @param comment      审批意见
     * @param actedBy      处理人
     * @param actedAt      处理时间
     */
    public record Task(
            Long id,
            Integer stepNo,
            String approverRole,
            Long approverUserId,
            String status,
            String comment,
            Long actedBy,
            LocalDateTime actedAt
    ) {

        /** 从实体转换任务视图。 */
        public static Task from(ApprovalTask task) {
            return new Task(task.getId(), task.getStepNo(), task.getApproverRole(), task.getApproverUserId(),
                    task.getStatus(), task.getComment(), task.getActedBy(), task.getActedAt());
        }
    }

    /**
     * 审批轨迹视图。
     *
     * @param id           记录 ID
     * @param action       动作
     * @param operatorUserId 操作人
     * @param operatorName 操作人姓名
     * @param comment      说明
     * @param createdAt    发生时间
     */
    public record Record(
            Long id,
            String action,
            Long operatorUserId,
            String operatorName,
            String comment,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 从实体转换实例视图。
     *
     * @param instance      审批实例
     * @param definitionName 命中的审批定义名称
     * @param tasks         审批任务
     * @param records       审批轨迹
     */
    public static ApprovalView from(ApprovalInstance instance, String definitionName,
                                    List<Task> tasks, List<Record> records) {
        return new ApprovalView(instance.getId(), instance.getInstanceNo(), instance.getBusinessType(),
                instance.getBusinessId(), instance.getTitle(), instance.getAmount(), instance.getFactoryId(),
                instance.getApplicantUserId(), instance.getApplicantName(), instance.getStatus(),
                instance.getDefinitionId(), definitionName, instance.getCurrentStep(), instance.getPayload(),
                instance.getResultNote(), tasks, records, instance.getCreatedAt(), instance.getFinishedAt(),
                instance.getUpdatedAt());
    }
}
