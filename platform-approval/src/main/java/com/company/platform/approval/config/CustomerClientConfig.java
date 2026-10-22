package com.company.platform.approval.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 客户服务调用配置。
 *
 * <p>通过 {@code @LoadBalanced} 让 RestTemplate 把 {@code http://platform-customer}
 * 解析为 Nacos 中的实例，避免硬编码 IP。审批通过后要落地业务变更，因此设置
 * 较短超时并快速失败，失败时审批事务回滚。</p>
 */
@Configuration
public class CustomerClientConfig {

    /**
     * 带负载均衡的 RestTemplate。
     *
     * <p>超时时间使用 {@code app.customer-service.*} 配置，开发环境 2 秒连接、
     * 5 秒读取。</p>
     */
    @Bean
    @LoadBalanced
    public RestTemplate customerRestTemplate(
            @org.springframework.beans.factory.annotation.Value("${app.customer-service.connect-timeout-ms:2000}")
            int connectTimeoutMs,
            @org.springframework.beans.factory.annotation.Value("${app.customer-service.read-timeout-ms:5000}")
            int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    /**
     * 带负载均衡的 RestTemplate（订单服务）。
     *
     * <p>插队审批通过后需要订单服务重排设备队列，因此审批中心会回调订单服务；
     * 超时与客户服务保持一致，失败时审批事务回滚。</p>
     */
    @Bean
    @LoadBalanced
    public RestTemplate orderRestTemplate(
            @org.springframework.beans.factory.annotation.Value("${app.order-service.connect-timeout-ms:2000}")
            int connectTimeoutMs,
            @org.springframework.beans.factory.annotation.Value("${app.order-service.read-timeout-ms:5000}")
            int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
