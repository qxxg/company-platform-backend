package com.company.platform.order.config;

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
 * 订单服务安全配置。
 *
 * <p>订单服务只接受 Bearer JWT，不建立 Session；方法级权限使用
 * {@code @PreAuthorize} 校验 permissions 声明。CORS 由公共模块统一配置，
 * 直接访问订单服务端口时也能被浏览器联调。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 构建无状态 JWT 资源服务器过滤链，并统一 401/403 JSON 响应。 */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/api/order/orders/health",
                                "/api/iot/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(JwtAuthenticationConverterFactory.create())));
        return http.build();
    }
}
