package com.company.platform.approval.client;

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

import java.util.Map;

/**
 * 订单服务调用客户端（审批中心 → 订单服务）。
 *
 * <p>设备队列插队审批通过后需要重排设备队列，这是订单服务的能力：审批中心
 * 带着审批人身份调用，订单服务据此校验工厂范围并记录审批痕迹。</p>
 */
@Component
public class OrderServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OrderServiceClient(@Qualifier("orderRestTemplate") RestTemplate restTemplate,
                              ObjectMapper objectMapper,
                              @Value("${app.order-service.base-url:http://platform-order}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 审批通过：重排设备队列并同步申请状态。
     *
     * @param jumpRequestId 插队申请 ID
     * @param remark        审批意见
     */
    public void applyQueueJump(long jumpRequestId, String remark) {
        post("/api/order/jump-requests/" + jumpRequestId + "/apply-approval", remark);
    }

    /**
     * 审批驳回：同步插队申请状态。
     *
     * @param jumpRequestId 插队申请 ID
     * @param remark        驳回意见
     */
    public void rejectQueueJump(long jumpRequestId, String remark) {
        post("/api/order/jump-requests/" + jumpRequestId + "/reject-approval", remark);
    }

    /**
     * 申请人撤销：同步插队申请状态。
     *
     * @param jumpRequestId 插队申请 ID
     */
    public void cancelQueueJump(long jumpRequestId) {
        try {
            restTemplate.exchange(baseUrl + "/api/order/jump-requests/" + jumpRequestId + "/cancel-approval",
                    HttpMethod.POST, new HttpEntity<>(jsonHeaders()), String.class);
        } catch (RestClientResponseException ex) {
            throw new BizException(ex.getStatusCode().value(), "插队申请状态同步失败：" + ex.getMessage());
        } catch (ResourceAccessException ex) {
            throw new BizException(503, "订单服务暂时不可用，审批结论未生效，请稍后重试");
        }
    }

    /** 提交带审批意见的请求。 */
    private void post(String path, String remark) {
        try {
            restTemplate.exchange(baseUrl + path, HttpMethod.POST,
                    new HttpEntity<>(writeJson(remark), jsonHeaders()), String.class);
        } catch (RestClientResponseException ex) {
            throw new BizException(ex.getStatusCode().value(), "插队申请状态同步失败：" + ex.getMessage());
        } catch (ResourceAccessException ex) {
            throw new BizException(503, "订单服务暂时不可用，审批结论未生效，请稍后重试");
        }
    }

    /** 请求头：透传当前审批人的 JWT，并声明 JSON 内容类型。 */
    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(SecurityUtils.currentJwt().getTokenValue());
        return headers;
    }

    /** 序列化审批意见。 */
    private String writeJson(String remark) {
        try {
            return objectMapper.writeValueAsString(Map.of("remark", remark == null ? "" : remark));
        } catch (Exception ex) {
            throw new BizException(500, "审批意见序列化失败");
        }
    }
}
