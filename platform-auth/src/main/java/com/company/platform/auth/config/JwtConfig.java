package com.company.platform.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * JWT 签发与校验配置。
 *
 * <p>认证中心从 PKCS#12 密钥库读取 RSA 私钥签发 JWT，并暴露 JWKS 公钥给网关和
 * 各资源服务器。生产环境必须替换仓库内的开发密钥库，并通过环境变量注入密码。</p>
 */
@Configuration
public class JwtConfig {

    /**
     * 加载 RSA 密钥对并包装为 Nimbus JWKSource。
     *
     * <p>keyID 固定为 {@code platform-auth-key}，会写入 JWT 头部 kid；
     * 资源服务器据此从 JWKS 中选择正确的公钥。</p>
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(
            @Value("${app.auth.keystore}") Resource keystoreResource,
            @Value("${app.auth.store-password}") String storePassword,
            @Value("${app.auth.key-alias}") String keyAlias,
            @Value("${app.auth.key-password}") String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream inputStream = keystoreResource.getInputStream()) {
            keyStore.load(inputStream, storePassword.toCharArray());
        }

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, keyPassword.toCharArray());
        PublicKey publicKey = keyStore.getCertificate(keyAlias).getPublicKey();
        if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey) || !(publicKey instanceof RSAPublicKey rsaPublicKey)) {
            throw new IllegalStateException("The configured key is not an RSA key pair");
        }

        RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey)
                .privateKey(rsaPrivateKey)
                .keyID("platform-auth-key")
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /** JWT 编码器，使用认证中心私钥签名。 */
    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /** 认证中心自身的 JWT 解码器，用于 OIDC UserInfo 等场景。 */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * 授权服务器元数据中的 issuer。必须与前端访问地址一致，
     * 否则 OIDC 发现、令牌 iss 校验和回调链路都会不一致。
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${app.auth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    /**
     * 配置 JWT 生成器并挂载平台声明定制器（userId、username、roles、permissions 等）。
     */
    @Bean
    public JwtGenerator jwtGenerator(JwtEncoder jwtEncoder,
                                     OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(tokenCustomizer);
        return jwtGenerator;
    }

    /**
     * 组合令牌生成器：访问令牌优先使用 JWT，刷新令牌使用不透明随机值。
     *
     * <p>公开客户端默认不会获得刷新令牌；此处的 RefreshTokenGenerator 主要为
     * 未来受信任客户端预留。</p>
     */
    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(JwtGenerator jwtGenerator) {
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, accessTokenGenerator, refreshTokenGenerator);
    }
}
