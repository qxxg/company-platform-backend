package com.company.platform.order.client;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批中心调用客户端（订单服务 → 审批中心）。
 *
 * <p>插队申请是审批业务：申请创建后把审批单提交给审批中心，由审批人在统一待办
 * 审批。调用时透传当前用户 JWT，审批中心据此记录申请人与数据范围。</p>
 *
 * <p>审批中心在“提交审批”阶段不会回调订单服务，因此这里不存在嵌套调用；
 * 队列重排发生在审批通过后由审批中心回调订单服务执行。</p>
 */
@Component
public class ApprovalClient {

    /** 插队申请的业务类型。 */
    public static final String BUSINESS_TYPE_QUEUE_JUMP = "QUEUE_JUMP";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public ApprovalClient(@Qualifier("approvalRestTemplate") RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          @Value("${app.approval-service.base-url:http://platform-approval}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 提交插队审批申请。
     *
     * @param requestNo     插队申请单号
     * @param jumpRequestId 插队申请 ID
     * @param workOrderId   工单 ID
     * @param workOrderNo   工单号
     * @param deviceNo      设备编号
     * @param targetPosition 目标队列位置
     * @param reason        插队原因
     * @return 审批单号
     */
    public String submitQueueJump(String requestNo, long jumpRequestId, long workOrderId, String workOrderNo,
                                  String deviceNo, int targetPosition, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jumpRequestId", jumpRequestId);
        payload.put("workOrderId", workOrderId);
        payload.put("workOrderNo", workOrderNo);
        payload.put("deviceNo", deviceNo);
        payload.put("targetPosition", targetPosition);
        payload.put("reason", reason);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("businessType", BUSINESS_TYPE_QUEUE_JUMP);
        body.put("businessId", String.valueOf(jumpRequestId));
        body.put("title", "设备队列插队申请：" + workOrderNo + " → 第 " + targetPosition + " 位");
        body.put("amount", null);
        body.put("payload", payload);
        String json = writeJson(body);
        try {
            String response = restTemplate.exchange(baseUrl + "/api/approval/instances",
                    HttpMethod.POST, new HttpEntity<>(json, jsonHeaders()), String.class).getBody();
            if (response == null) {
                throw new BizException(503, "审批中心返回空响应，插队申请未提交");
            }
            Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
            Map<?, ?> data = (Map<?, ?>) parsed.get("data");
            Object instanceNo = data == null ? null : data.get("instanceNo");
            return instanceNo == null ? "" : String.valueOf(instanceNo);
        } catch (RestClientResponseException ex) {
            throw new BizException(ex.getStatusCode().value(), "插队申请提交审批失败：" + ex.getMessage());
        } catch (ResourceAccessException ex) {
            throw new BizException(503, "审批中心暂时不可用，插队申请未提交，请稍后重试");
        } catch (java.io.IOException ex) {
            throw new BizException(503, "审批中心响应解析失败，插队申请未提交");
        }
    }

    /** 请求头：透传当前用户 JWT，并声明 JSON 内容类型。 */
    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(SecurityUtils.currentJwt().getTokenValue());
        return headers;
    }

    /** 序列化请求体。 */
    private String writeJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new BizException(500, "审批申请参数序列化失败");
        }
    }
}
