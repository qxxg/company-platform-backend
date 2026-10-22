package com.company.platform.approval.service;

/**
 * 审批通过后的业务动作执行器。
 *
 * <p>审批中心只负责流程与结论；具体业务变更（例如客户信用额度调整）由对应业务
 * 服务的处理器落地。新增业务类型时实现本接口并注册为 Spring Bean 即可，审批
 * 通过时会自动调用；没有处理器的业务类型只记录审批结论。</p>
 */
public interface ApprovalActionHandler {

    /**
     * 本处理器支持的业务类型。
     *
     * @return 业务类型编码，与审批实例的 businessType 一致
     */
    String businessType();

    /**
     * 审批通过后执行实际业务变更。
     *
     * <p>执行失败会抛出异常，审批事务整体回滚，实例保持待审批，避免出现“审批
     * 通过但业务未落地”的中间状态。</p>
     *
     * @param instance 审批实例
     * @param comment  审批意见，可为空
     */
    void onApproved(com.company.platform.approval.entity.ApprovalInstance instance, String comment);

    /**
     * 审批驳回后的业务同步。
     *
     * <p>默认不处理：多数业务在驳回时不需要改动业务数据。需要同步状态（例如
     * 插队申请标记为已驳回）的业务类型再覆写。</p>
     *
     * @param instance 审批实例
     * @param comment  驳回意见
     */
    default void onRejected(com.company.platform.approval.entity.ApprovalInstance instance, String comment) {
        // 默认无业务副作用。
    }

    /**
     * 申请人撤销后的业务同步。
     *
     * <p>默认不处理，语义与驳回同步一致。</p>
     *
     * @param instance 审批实例
     */
    default void onCancelled(com.company.platform.approval.entity.ApprovalInstance instance) {
        // 默认无业务副作用。
    }
}
