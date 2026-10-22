package com.company.platform.approval.config;

import com.company.platform.common.security.JwtAuthenticationConverterFactory;
import com.company.platform.common.security.RestAccessDeniedHandler;
import com.company.platform.common.security.RestAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 审批中心安全配置。
 *
 * <p>与系统服务一致：作为 OAuth2 资源服务器校验 JWT，并把 permissions 声明映射
 * 为 {@code @PreAuthorize} 可直接使用的 authority；只有健康检查端点放行。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 构建无状态资源服务器过滤链。
     *
     * <p>统一 401/403 JSON 由 common-security 提供，前端可以直接按 ApiResponse
     * 的 code 处理，而不必解析 HTML 错误页。</p>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/api/approval/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(JwtAuthenticationConverterFactory.create())));
        return http.build();
    }
}
