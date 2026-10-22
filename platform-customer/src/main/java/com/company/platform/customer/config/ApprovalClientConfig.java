package com.company.platform.customer.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 审批中心调用配置。
 *
 * <p>费用报销和借款提交后需要把审批单交给统一审批中心，{@code @LoadBalanced}
 * 让 RestTemplate 把 {@code http://platform-approval} 解析为 Nacos 中的实例。
 * 提交阶段审批中心不会回调本服务，因此不存在嵌套调用死锁；审批通过后的回调
 * 是另一个独立请求。</p>
 */
@Configuration
public class ApprovalClientConfig {

    /**
     * 带负载均衡的 RestTemplate（审批中心）。
     *
     * <p>连接 2 秒、读取 5 秒，审批中心不可用时快速失败并回滚业务事务。</p>
     */
    @Bean
    @LoadBalanced
    public RestTemplate approvalRestTemplate(
            @org.springframework.beans.factory.annotation.Value("${app.approval-service.connect-timeout-ms:2000}")
            int connectTimeoutMs,
            @org.springframework.beans.factory.annotation.Value("${app.approval-service.read-timeout-ms:5000}")
            int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
