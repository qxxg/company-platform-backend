package com.company.platform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关启动类。
 *
 * <p>职责：统一入口、JWT 验签、CORS、请求 ID 透传和路由转发。网关只扫描
 * {@code com.company.platform.gateway}，避免把 Servlet 侧的安全配置带入 WebFlux 上下文。</p>
 *
 * <p>路由通过 Nacos 服务发现访问 {@code lb://platform-system} 与
 * {@code lb://platform-order}，端口 8080。</p>
 */
@SpringBootApplication(scanBasePackages = "com.company.platform.gateway")
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
