package com.company.platform.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.company.platform.ai.config.AiProperties;

/**
 * AI 助手服务启动类。
 *
 * <p>本服务与业务服务共享认证中心 JWT。AI 不以超级用户身份访问数据库，而是把
 * 当前用户令牌透传给订单、客户、审批等服务，因此数据范围、权限校验和审计主体
 * 都与用户直接操作时保持一致。</p>
 */
@SpringBootApplication(scanBasePackages = "com.company.platform")
@EnableConfigurationProperties(AiProperties.class)
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
