package com.company.platform.order.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 客户服务调用配置。
 *
 * <p>通过 {@code @LoadBalanced} 让 RestTemplate 把
 * {@code http://platform-customer} 解析为 Nacos 中的实例，避免硬编码 IP。
 * 订单创建必须完成客户和合同校验，因此超时设置较短并快速失败。</p>
 */
@Configuration
public class CustomerClientConfig {

    /**
     * 带负载均衡的 RestTemplate。
     *
     * <p>超时时间使用 ${@code app.customer-service.*} 配置，开发环境 2 秒连接、
     * 5 秒读取；生产可按内网 RTT 调整。不要把它做成无限等待，否则网关和订单
     * 线程都会被拖住。</p>
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
}
