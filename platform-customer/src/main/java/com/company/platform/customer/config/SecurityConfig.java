package com.company.platform.customer.config;

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
 * 客户服务安全配置。
 *
 * <p>本服务是 JWT 资源服务器。客户接口按权限码控制，数据范围（本人客户、
 * 全部客户）在业务服务内继续校验，不能只依赖菜单隐藏。</p>
 *
 * <p>订单服务会带上当前登录人的 Bearer Token 调用下单上下文接口，因此这里
 * 不信任服务间固定 IP，而是保留真实用户身份和权限。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 构建无状态过滤链，健康检查公开，其余请求必须携带有效 JWT。 */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/api/customer/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(JwtAuthenticationConverterFactory.create())));
        return http.build();
    }
}
