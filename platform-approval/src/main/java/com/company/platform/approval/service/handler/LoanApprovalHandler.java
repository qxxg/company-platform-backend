package com.company.platform.approval.service.handler;

import com.company.platform.approval.client.CustomerServiceClient;
import com.company.platform.approval.entity.ApprovalInstance;
import com.company.platform.approval.service.ApprovalActionHandler;
import com.company.platform.common.core.api.BizException;
import org.springframework.stereotype.Component;

/**
 * 借款审批处理器。
 *
 * <p>审批通过只把借款推进到“待放款”，真正的放款、还款由财务在借款页面
 * 登记。审批驳回或申请人撤销时同步把借款单置为对应状态。</p>
 */
@Component
public class LoanApprovalHandler implements ApprovalActionHandler {

    /** 业务类型：借款。 */
    public static final String BUSINESS_TYPE = "LOAN";

    private final CustomerServiceClient customerServiceClient;

    public LoanApprovalHandler(CustomerServiceClient customerServiceClient) {
        this.customerServiceClient = customerServiceClient;
    }

    @Override
    public String businessType() {
        return BUSINESS_TYPE;
    }

    @Override
    public void onApproved(ApprovalInstance instance, String comment) {
        customerServiceClient.applyLoanApproved(businessId(instance), comment);
    }

    @Override
    public void onRejected(ApprovalInstance instance, String comment) {
        customerServiceClient.applyLoanRejected(businessId(instance), comment);
    }

    @Override
    public void onCancelled(ApprovalInstance instance) {
        customerServiceClient.applyLoanCancelled(businessId(instance));
    }

    /** 业务主键即借款单 ID；缺失时直接报错并回滚审批事务。 */
    private long businessId(ApprovalInstance instance) {
        try {
            return Long.parseLong(instance.getBusinessId());
        } catch (NumberFormatException ex) {
            throw new BizException(400, "借款审批单缺少有效业务主键");
        }
    }
}
