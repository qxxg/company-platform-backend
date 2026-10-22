package com.company.platform.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 订单服务启动类。
 *
 * <p>职责：订单创建、详情查询、按客户查询和分页查询。订单号采用雪花算法生成，
 * 由 ShardingSphere-JDBC 按 order_no 路由到两个 MySQL 库、共 8 张物理表。</p>
 *
 * <p>注册到 Nacos 的服务名为 {@code platform-order}，端口 9102。</p>
 */
@SpringBootApplication(scanBasePackages = "com.company.platform")
@EnableDiscoveryClient
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
