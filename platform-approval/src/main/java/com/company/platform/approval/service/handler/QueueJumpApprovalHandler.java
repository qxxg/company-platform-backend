package com.company.platform.approval.service.handler;

import com.company.platform.common.core.api.BizException;
import com.company.platform.approval.client.OrderServiceClient;
import com.company.platform.approval.entity.ApprovalInstance;
import com.company.platform.approval.service.ApprovalActionHandler;
import com.company.platform.approval.service.ApprovalPayloadReader;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 设备队列插队处理器。
 *
 * <p>插队申请由销售发起、厂长审批：通过后由订单服务重排设备队列并给被顺延的
 * 业务员发送站内消息；驳回或撤销时同步插队申请状态，业务数据不变。</p>
 */
@Component
public class QueueJumpApprovalHandler implements ApprovalActionHandler {

    /** 业务类型：设备队列插队。 */
    public static final String BUSINESS_TYPE = "QUEUE_JUMP";

    private final OrderServiceClient orderServiceClient;
    private final ApprovalPayloadReader payloadReader;

    public QueueJumpApprovalHandler(OrderServiceClient orderServiceClient,
                                    ApprovalPayloadReader payloadReader) {
        this.orderServiceClient = orderServiceClient;
        this.payloadReader = payloadReader;
    }

    @Override
    public String businessType() {
        return BUSINESS_TYPE;
    }

    @Override
    public void onApproved(ApprovalInstance instance, String comment) {
        long jumpRequestId = jumpRequestIdOf(instance);
        orderServiceClient.applyQueueJump(jumpRequestId, comment);
    }

    @Override
    public void onRejected(ApprovalInstance instance, String comment) {
        orderServiceClient.rejectQueueJump(jumpRequestIdOf(instance), comment);
    }

    @Override
    public void onCancelled(ApprovalInstance instance) {
        orderServiceClient.cancelQueueJump(jumpRequestIdOf(instance));
    }

    /** 从参数快照中取插队申请 ID。 */
    private long jumpRequestIdOf(ApprovalInstance instance) {
        Map<String, Object> payload = payloadReader.read(instance.getPayload());
        Object jumpRequestId = payload.get("jumpRequestId");
        if (jumpRequestId == null) {
            throw new BizException(400, "插队审批缺少申请单 ID，无法落地");
        }
        return Long.parseLong(String.valueOf(jumpRequestId));
    }
}
