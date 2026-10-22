package com.company.platform.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * OAuth2 客户端与授权数据持久化配置。
 *
 * <p>客户端注册信息、授权记录和用户同意记录都存放在 MySQL 的 OAuth2 标准表中，
 * 认证中心重启不会丢失。当前维护两类客户端：</p>
 *
 * <ul>
 *   <li>{@code pc-web}：浏览器公开客户端，授权码 + PKCE，无 client_secret；</li>
 *   <li>{@code ops-client}：运维/脚本客户端，客户端凭证模式，有 secret。</li>
 * </ul>
 */
@Configuration
public class OAuth2ClientConfig {

    /**
     * 运维脚本客户端可申请的 scope 集合。
     *
     * <p>scope 与权限码的映射在 {@code PlatformTokenCustomizer} 中定义，例如
     * {@code finance.write} 展开为合同、应收、收款的写权限。客户端凭证令牌没有
     * 用户身份，按组织范围访问，能访问哪些接口完全由这里授予的 scope 决定。</p>
     */
    private static final List<String> OPS_CLIENT_SCOPES = List.of(
            "system.read", "system.write",
            "order.read", "order.write", "order.approve",
            "finance.read", "finance.write",
            "customer.read",
            "production.read", "production.write", "production.approve",
            "equipment.maintenance",
            "warehouse.read", "warehouse.write",
            "approval.read", "approval.submit", "approval.approve", "approval.manage",
            "ai.use", "bi.read");

    /**
     * 创建 JDBC 客户端仓库，并在首次启动时写入内置客户端。
     *
     * <p>如果 pc-web 已存在，则补齐内网开发回调地址。这样从 localhost 或
     * 192.168.3.43 访问前端都能完成授权码流程，无需手工改库。</p>
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.clients.ops-secret}") String opsClientSecret) {
        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcTemplate);

        RegisteredClient pcWeb = repository.findByClientId("pc-web");
        if (pcWeb == null) {
            repository.save(pcWebClient());
        } else {
            // 开发机可能通过 localhost 或内网 IP 打开前端。已存在的客户端也要补齐
            // 回调地址，否则 OAuth2 授权码流程会被 redirect_uri 校验拒绝。
            repository.save(RegisteredClient.from(pcWeb)
                    .redirectUri("http://192.168.3.43:5173/login/oauth2/code/pc-web")
                    .postLogoutRedirectUri("http://192.168.3.43:5173/")
                    .build());
        }
        RegisteredClient ops = repository.findByClientId("ops-client");
        if (ops == null) {
            repository.save(opsClient(passwordEncoder, opsClientSecret));
        } else {
            // 已存在的客户端补齐代码中新增的 scope，避免数据库里的旧客户端缺少
            // 财务等权限范围，导致脚本调用时被判定为 invalid_scope。
            repository.save(RegisteredClient.from(ops)
                    .scopes(scopes -> scopes.addAll(OPS_CLIENT_SCOPES))
                    .build());
        }
        return repository;
    }

    /**
     * 授权记录持久化。JDBC 实现会把 authorization code、access token 元数据
     * 和登录主体写入 oauth2_authorization 表，支持多实例部署。
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    /** 用户授权同意记录持久化；当前 pc-web 关闭了授权确认页。 */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * PC Web 公开客户端定义。
     *
     * <p>访问令牌 30 分钟，ID Token 使用 RS256。要求 PKCE（S256），防止授权码
     * 在重定向过程中被截获后直接兑换。公开客户端不配置 client_secret。</p>
     */
    private static RegisteredClient pcWebClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("pc-web")
                .clientName("Company Platform PC Web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:5173/login/oauth2/code/pc-web")
                .redirectUri("http://127.0.0.1:5173/login/oauth2/code/pc-web")
                .redirectUri("http://192.168.3.43:5173/login/oauth2/code/pc-web")
                .postLogoutRedirectUri("http://localhost:5173/")
                .postLogoutRedirectUri("http://192.168.3.43:5173/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("system.read")
                .scope("system.write")
                .scope("order.read")
                .scope("order.write")
                .scope("order.approve")
                .scope("finance.read")
                .scope("finance.write")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .refreshTokenTimeToLive(Duration.ofHours(8))
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .build())
                .build();
    }

    /**
     * 运维脚本客户端定义。仅用于冒烟测试、内部任务和运维工具，
     * 令牌有效期 15 分钟，不具备刷新令牌能力。
     */
    private static RegisteredClient opsClient(PasswordEncoder passwordEncoder, String clientSecret) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("ops-client")
                .clientName("Company Platform Operations Client")
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes(scopes -> scopes.addAll(OPS_CLIENT_SCOPES))
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .build())
                .build();
    }
}
