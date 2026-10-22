package com.company.platform.ai.config;

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
 * AI 助手安全配置。
 *
 * <p>本服务不信任前端传入的用户 ID、角色或数据范围，只接受认证中心签发的
 * JWT；控制器再通过 {@code ai:assistant:use} 做一层方法级权限校验。健康检查
 * 端点放行，方便启动脚本判断服务是否可用；模型状态端点仍要求登录和权限。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 无状态资源服务器过滤链，401/403 统一返回 JSON。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/api/ai/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(JwtAuthenticationConverterFactory.create())));
        return http.build();
    }
}
