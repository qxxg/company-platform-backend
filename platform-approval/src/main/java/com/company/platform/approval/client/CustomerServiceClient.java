package com.company.platform.approval.client;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 客户服务调用客户端（审批中心 → 客户服务）。
 *
 * <p>审批通过后需要落地业务变更（例如客户信用额度调整）。调用时透传当前审批人
 * 的 JWT，客户服务因此可以按人校验权限并留下操作人痕迹；客户服务不可用时
 * 审批事务回滚，避免出现“审批通过但业务未变更”。</p>
 */
@Component
public class CustomerServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public CustomerServiceClient(@Qualifier("customerRestTemplate") RestTemplate restTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${app.customer-service.base-url:http://platform-customer}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 落地客户信用额度调整。
     *
     * @param customerId  客户 ID
     * @param creditLimit 新的信用额度：分
     */
    public void applyCreditLimit(long customerId, long creditLimit) {
        Map<String, Object> body = Map.of("creditLimit", creditLimit);
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new BizException(500, "客户信用额度调整参数序列化失败");
        }
        String path = "/api/customer/customers/" + customerId + "/credit-limit/apply";
        try {
            restTemplate.exchange(baseUrl + path, org.springframework.http.HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(json, jsonHeaders()),
                    String.class);
        } catch (RestClientResponseException ex) {
            throw new BizException(ex.getStatusCode().value(), "客户信用额度调整失败：" + ex.getMessage());
        } catch (ResourceAccessException ex) {
            throw new BizException(503, "客户服务暂时不可用，审批未生效，请稍后重试");
        }
    }

    /** 费用报销审批通过：审批中 → 待付款。 */
    public void applyExpenseApproved(long claimId, String comment) {
        post("/api/finance/expenses/" + claimId + "/approval/approved", commentBody(comment),
                "费用报销审批通过");
    }

    /** 费用报销审批驳回：审批中 → 已驳回。 */
    public void applyExpenseRejected(long claimId, String comment) {
        post("/api/finance/expenses/" + claimId + "/approval/rejected", commentBody(comment),
                "费用报销审批驳回");
    }

    /** 费用报销申请撤销：审批中 → 已取消。 */
    public void applyExpenseCancelled(long claimId) {
        post("/api/finance/expenses/" + claimId + "/approval/cancelled", Map.of(),
                "费用报销撤销");
    }

    /** 借款审批通过：审批中 → 待放款。 */
    public void applyLoanApproved(long loanId, String comment) {
        post("/api/finance/loans/" + loanId + "/approval/approved", commentBody(comment),
                "借款审批通过");
    }

    /** 借款审批驳回：审批中 → 已驳回。 */
    public void applyLoanRejected(long loanId, String comment) {
        post("/api/finance/loans/" + loanId + "/approval/rejected", commentBody(comment),
                "借款审批驳回");
    }

    /** 借款申请撤销：审批中 → 已取消。 */
    public void applyLoanCancelled(long loanId) {
        post("/api/finance/loans/" + loanId + "/approval/cancelled", Map.of(),
                "借款撤销");
    }

    /**
     * 统一回调入口。
     *
     * <p>客户服务不可用或返回业务错误时抛出异常，让审批事务回滚，保证审批结论
     * 与业务状态始终一致。</p>
     */
    private void post(String path, Map<String, Object> body, String label) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new BizException(500, label + "回调参数序列化失败");
        }
        try {
            restTemplate.exchange(baseUrl + path, org.springframework.http.HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(json, jsonHeaders()),
                    String.class);
        } catch (RestClientResponseException ex) {
            throw new BizException(ex.getStatusCode().value(),
                    label + "同步失败：" + ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new BizException(503, "客户服务暂时不可用，审批未生效，请稍后重试");
        }
    }

    /** 审批意见请求体；意见为空时传空对象。 */
    private Map<String, Object> commentBody(String comment) {
        return comment == null || comment.isBlank() ? Map.of() : Map.of("comment", comment);
    }

    /** 请求头：透传当前审批人的 JWT。 */
    private org.springframework.http.HttpHeaders jsonHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token());
        return headers;
    }

    /** 当前审批人的令牌：透传给客户服务做权限校验。 */
    private String token() {
        return SecurityUtils.currentJwt().getTokenValue();
    }
}
