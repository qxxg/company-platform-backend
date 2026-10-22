package com.company.platform.order.client;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.api.BizException;
import com.company.platform.common.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * 客户服务内部调用客户端。
 *
 * <p>订单创建必须在服务端完成“客户状态 + 合同已录入 + 信用额度 + 逾期”
 * 校验，不能只依赖前端。调用时透传当前用户 JWT，客户服务因此可以继续执行
 * 业务员数据范围校验，而不是信任订单服务的调用方。</p>
 *
 * <p>客户服务不可用时订单创建失败关闭（fail-closed），避免绕过风控。</p>
 */
@Component
public class CustomerServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public CustomerServiceClient(RestTemplate customerRestTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${app.customer-service.base-url:http://platform-customer}") String baseUrl) {
        this.restTemplate = customerRestTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 获取下单客户上下文。
     *
     * @param customerId 客户 ID
     * @return 客户状态、信用额度和应收汇总
     */
    public CustomerOrderContext customerContext(long customerId) {
        return get("/api/customer/customers/" + customerId + "/order-context",
                new ParameterizedTypeReference<>() {
                });
    }

    /**
     * 获取下单合同上下文。
     *
     * @param contractId 合同 ID
     * @return 合同状态、归属客户、金额和有效期
     */
    public ContractOrderContext contractContext(long contractId) {
        return get("/api/customer/contracts/" + contractId + "/order-context",
                new ParameterizedTypeReference<>() {
                });
    }

    /** 统一 GET 调用，解析 ApiResponse 并把下游错误转换为业务异常。 */
    private <T> T get(String path, ParameterizedTypeReference<ApiResponse<T>> typeReference) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityUtils.currentJwt().getTokenValue());
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<ApiResponse<T>> response = restTemplate.exchange(
                    baseUrl + path, HttpMethod.GET, request, typeReference);
            ApiResponse<T> body = response.getBody();
            if (body == null) {
                throw new BizException(503, "客户服务返回空响应，请稍后重试");
            }
            if (body.code() != 0) {
                throw new BizException(body.code(), body.message());
            }
            return body.data();
        } catch (RestClientResponseException ex) {
            throw translateError(ex);
        } catch (ResourceAccessException | IllegalStateException ex) {
            throw new BizException(503, "客户服务暂时不可用，订单未创建，请稍后重试");
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new BizException(503, "客户服务调用失败，订单未创建，请稍后重试");
        }
    }

    /**
     * 尽量保留客户服务的业务错误文案（例如“客户状态为暂停”），而不是把
     * 所有 4xx 都改写成“系统错误”。解析失败时使用通用提示和 HTTP 状态码。
     */
    private BizException translateError(RestClientResponseException ex) {
        try {
            ApiResponse<?> body = objectMapper.readValue(ex.getResponseBodyAsString(),
                    new TypeReference<ApiResponse<?>>() {
                    });
            if (body != null && body.message() != null && !body.message().isBlank()) {
                return new BizException(body.code() == 0 ? ex.getStatusCode().value() : body.code(), body.message());
            }
        } catch (Exception ignored) {
            // 下游返回 HTML 或空体时继续走通用提示。
        }
        return new BizException(ex.getStatusCode().value(), "客户或合同校验失败（HTTP "
                + ex.getStatusCode().value() + "），订单未创建");
    }
}
