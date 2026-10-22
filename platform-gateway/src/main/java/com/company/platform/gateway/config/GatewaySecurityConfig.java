package com.company.platform.gateway.config;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.security.PlatformClaims;
import com.company.platform.common.core.trace.TraceId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关安全配置（WebFlux）。
 *
 * <p>网关是系统唯一对外入口：先校验 JWT 签名和有效期，再把请求转发到
 * Nacos 中的业务服务。健康检查与 {@code /api/public/**} 放行，其余请求
 * 必须携带 Bearer Token。</p>
 *
 * <p>网关不做细粒度业务权限判断，接口级 {@code @PreAuthorize} 仍在各服务
 * 执行；这里只负责认证和统一 401/403 JSON。</p>
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /** 构建响应式安全过滤链。CORS 使用本类下方更宽松的网关级配置。 */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ObjectMapper objectMapper) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/info", "/api/public/**", "/api/iot/**")
                        .permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, exception) ->
                                writeError(exchange, objectMapper, 401, "Unauthorized"))
                        .accessDeniedHandler((exchange, exception) ->
                                writeError(exchange, objectMapper, 403, "Forbidden")))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * 响应式 JWT 权限转换器：permissions 原样保留，roles 增加 ROLE_ 前缀。
     * 与 Servlet 服务的规则保持一致，避免同一令牌在网关和业务服务中权限不同。
     */
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter permissionsConverter = new JwtGrantedAuthoritiesConverter();
        permissionsConverter.setAuthoritiesClaimName(PlatformClaims.PERMISSIONS);
        permissionsConverter.setAuthorityPrefix("");

        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName(PlatformClaims.ROLES);
        rolesConverter.setAuthorityPrefix("ROLE_");

        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                Flux.concat(Flux.fromIterable(permissionsConverter.convert(jwt)),
                        Flux.fromIterable(rolesConverter.convert(jwt))));
        return converter;
    }

    /**
     * 网关级 CORS。这里允许所有来源，方便内网多台机器和不同端口联调；
     * 生产环境应在 Nginx 或本配置中收敛为明确域名，并开启 HTTPS。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(TraceId.HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 直接向响应写入 JSON，避免 WebFlux 默认空响应导致前端无法读取错误信息。
     * 序列化失败时退化为最小 JSON，保证状态码仍然正确。
     */
    private static Mono<Void> writeError(org.springframework.web.server.ServerWebExchange exchange,
                                         ObjectMapper objectMapper, int status, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiResponse.error(status, message));
        } catch (JsonProcessingException exception) {
            body = ("{\"code\":" + status + ",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
