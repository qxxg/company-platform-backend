package com.company.platform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 认证中心启动类。
 *
 * <p>职责：提供 OAuth2 授权码 + PKCE、客户端凭证、OIDC 发现、JWKS 公钥，
 * 并把平台用户、角色、权限写入 JWT。业务服务只信任本服务签发的令牌。</p>
 *
 * <p>注册到 Nacos 的服务名为 {@code platform-auth}，端口 9000。</p>
 */
@SpringBootApplication(scanBasePackages = "com.company.platform")
@EnableDiscoveryClient
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
