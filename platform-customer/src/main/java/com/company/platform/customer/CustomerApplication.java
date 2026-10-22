package com.company.platform.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 客户与合同服务启动类。
 *
 * <p>本服务负责客户主数据、客户归属、合同正式录入和下单前置校验所需的客户
 * 信用上下文。图案库、报价单和客户 360 聚合在后续迭代继续扩展。</p>
 *
 * <p>服务名 {@code platform-customer}，端口 9103。订单服务通过 Nacos 服务名
 * 调用本服务，不直接依赖固定 IP。</p>
 */
@SpringBootApplication(scanBasePackages = "com.company.platform")
@EnableDiscoveryClient
public class CustomerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }
}
