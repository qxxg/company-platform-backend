package com.company.platform.approval.service.handler;

import com.company.platform.approval.client.CustomerServiceClient;
import com.company.platform.approval.entity.ApprovalInstance;
import com.company.platform.approval.service.ApprovalActionHandler;
import com.company.platform.common.core.api.BizException;
import org.springframework.stereotype.Component;

/**
 * 费用报销审批处理器。
 *
 * <p>审批中心只负责流程与结论；报销单的“待付款/已驳回/已取消”状态由本
 * 处理器调用客户服务落地。审批通过不会直接付款，财务仍需在报销页面登记
 * 付款，这样审批与执行分离。</p>
 */
@Component
public class ExpenseApprovalHandler implements ApprovalActionHandler {

    /** 业务类型：费用报销。 */
    public static final String BUSINESS_TYPE = "EXPENSE";

    private final CustomerServiceClient customerServiceClient;

    public ExpenseApprovalHandler(CustomerServiceClient customerServiceClient) {
        this.customerServiceClient = customerServiceClient;
    }

    @Override
    public String businessType() {
        return BUSINESS_TYPE;
    }

    @Override
    public void onApproved(ApprovalInstance instance, String comment) {
        customerServiceClient.applyExpenseApproved(businessId(instance), comment);
    }

    @Override
    public void onRejected(ApprovalInstance instance, String comment) {
        customerServiceClient.applyExpenseRejected(businessId(instance), comment);
    }

    @Override
    public void onCancelled(ApprovalInstance instance) {
        customerServiceClient.applyExpenseCancelled(businessId(instance));
    }

    /** 业务主键即报销单 ID；缺失时直接报错，避免审批通过后找不到单据。 */
    private long businessId(ApprovalInstance instance) {
        try {
            return Long.parseLong(instance.getBusinessId());
        } catch (NumberFormatException ex) {
            throw new BizException(400, "费用报销审批单缺少有效业务主键");
        }
    }
}
