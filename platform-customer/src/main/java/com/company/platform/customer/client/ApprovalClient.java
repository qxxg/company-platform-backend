package com.company.platform.customer.client;

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
 * 审批中心调用客户端（客户服务 → 审批中心）。
 *
 * <p>费用报销、借款都使用统一审批中心：业务服务只负责创建单据和持久化业务
 * 状态，金额档位匹配、待办、审批轨迹由审批中心处理。调用时透传当前用户 JWT，
 * 审批中心因此能识别申请人和数据范围。</p>
 */
@Component
public class ApprovalClient {

    /** 费用报销业务类型。 */
    public static final String BUSINESS_TYPE_EXPENSE = "EXPENSE";
    /** 借款业务类型。 */
    public static final String BUSINESS_TYPE_LOAN = "LOAN";

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
     * 提交审批申请。
     *
     * @param businessType 业务类型 EXPENSE/LOAN
     * @param businessId   业务主键（报销单/借款单 ID）
     * @param title        申请标题
     * @param amount       金额，单位分，用于匹配审批档位
     * @param factoryId    归属工厂，可空
     * @param payload      业务参数快照
     * @param label        单据名称，用于错误提示
     * @return 审批实例 ID 与单号
     */
    public ApprovalInstanceRef submit(String businessType, long businessId, String title, long amount,
                                      Long factoryId, Map<String, Object> payload, String label) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("businessType", businessType);
        body.put("businessId", String.valueOf(businessId));
        body.put("title", title);
        body.put("amount", amount);
        body.put("factoryId", factoryId);
        body.put("payload", payload);
        String json = writeJson(body);
        try {
            String response = restTemplate.exchange(baseUrl + "/api/approval/instances",
                    HttpMethod.POST, new HttpEntity<>(json, jsonHeaders()), String.class).getBody();
            if (response == null) {
                throw new BizException(503, "审批中心返回空响应，" + label + "未提交");
            }
            Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
            Map<?, ?> data = (Map<?, ?>) parsed.get("data");
            if (data == null || data.get("id") == null) {
                throw new BizException(503, "审批中心响应缺少审批实例，" + label + "未提交");
            }
            long instanceId = Long.parseLong(String.valueOf(data.get("id")));
            Object instanceNo = data.get("instanceNo");
            return new ApprovalInstanceRef(instanceId, instanceNo == null ? "" : String.valueOf(instanceNo));
        } catch (RestClientResponseException ex) {
            throw new BizException(ex.getStatusCode().value(), label + "提交审批失败：" + ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new BizException(503, "审批中心暂时不可用，" + label + "未提交，请稍后重试");
        } catch (java.io.IOException ex) {
            throw new BizException(503, "审批中心响应解析失败，" + label + "未提交");
        }
    }

    /**
     * 撤销待审批申请。
     *
     * <p>审批中心撤销成功后会回调业务服务把单据置为已取消；调用方不需要再改
     * 一次状态，避免与回调竞争。</p>
     */
    public void cancel(long instanceId, String label) {
        try {
            restTemplate.exchange(baseUrl + "/api/approval/instances/" + instanceId + "/cancel",
                    HttpMethod.POST, new HttpEntity<>(null, jsonHeaders()), String.class);
        } catch (RestClientResponseException ex) {
            throw new BizException(ex.getStatusCode().value(), label + "撤销审批失败：" + ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new BizException(503, "审批中心暂时不可用，" + label + "未撤销，请稍后重试");
        }
    }

    /** JSON 请求头，透传当前用户 JWT。 */
    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(SecurityUtils.currentJwt().getTokenValue());
        return headers;
    }

    /** 序列化请求体，失败时返回可读的业务异常。 */
    private String writeJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BizException(500, "审批请求序列化失败");
        }
    }

    /**
     * 审批实例引用。
     *
     * @param id         审批实例 ID
     * @param instanceNo 审批单号
     */
    public record ApprovalInstanceRef(long id, String instanceNo) {
    }
}
