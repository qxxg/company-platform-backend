package com.company.platform.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 系统服务启动类。
 *
 * <p>职责：提供用户、工厂等基础数据查询，并作为 JWT 资源服务器校验网关转发来的令牌。
 * 当前只实现查询能力，组织树、岗位、员工档案和账号维护在后续迭代补齐。</p>
 *
 * <p>注册到 Nacos 的服务名为 {@code platform-system}，端口 9101。</p>
 */
@SpringBootApplication(scanBasePackages = "com.company.platform")
@EnableDiscoveryClient
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
