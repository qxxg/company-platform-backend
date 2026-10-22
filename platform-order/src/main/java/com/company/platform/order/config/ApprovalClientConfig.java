package com.company.platform.order.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 审批中心调用配置。
 *
 * <p>插队申请创建后需要把审批单提交给审批中心，{@code @LoadBalanced} 让
 * RestTemplate 把 {@code http://platform-approval} 解析为 Nacos 中的实例。
 * 审批中心不会在提交审批时回调订单服务，因此不存在嵌套调用死锁。</p>
 */
@Configuration
public class ApprovalClientConfig {

    /**
     * 带负载均衡的 RestTemplate（审批中心）。
     *
     * <p>超时与客户服务保持一致：连接 2 秒、读取 5 秒，失败快速返回可读错误。</p>
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
