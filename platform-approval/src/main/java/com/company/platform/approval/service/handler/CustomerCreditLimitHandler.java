package com.company.platform.approval.service.handler;

import com.company.platform.common.core.api.BizException;
import com.company.platform.approval.client.CustomerServiceClient;
import com.company.platform.approval.entity.ApprovalInstance;
import com.company.platform.approval.service.ApprovalActionHandler;
import com.company.platform.approval.service.ApprovalPayloadReader;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 客户信用额度调整处理器。
 *
 * <p>信用额度属于审批字段，页面不能直接改数：销售或管理员提交调整申请，财务
 * 审批通过后由本处理器调用客户服务落地额度变更，并把审批人身份透传下去。</p>
 */
@Component
public class CustomerCreditLimitHandler implements ApprovalActionHandler {

    /** 业务类型：信用额度调整。 */
    public static final String BUSINESS_TYPE = "CREDIT_LIMIT";

    private final CustomerServiceClient customerServiceClient;
    private final ApprovalPayloadReader payloadReader;

    public CustomerCreditLimitHandler(CustomerServiceClient customerServiceClient,
                                      ApprovalPayloadReader payloadReader) {
        this.customerServiceClient = customerServiceClient;
        this.payloadReader = payloadReader;
    }

    @Override
    public String businessType() {
        return BUSINESS_TYPE;
    }

    @Override
    public void onApproved(ApprovalInstance instance, String comment) {
        Map<String, Object> payload = payloadReader.read(instance.getPayload());
        Object customerId = payload.get("customerId");
        Object creditLimit = payload.get("creditLimit");
        if (customerId == null || creditLimit == null) {
            throw new BizException(400, "信用额度调整申请缺少参数，无法落地");
        }
        customerServiceClient.applyCreditLimit(Long.parseLong(String.valueOf(customerId)),
                Long.parseLong(String.valueOf(creditLimit)));
    }
}
