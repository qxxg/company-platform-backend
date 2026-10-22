package com.company.platform.auth.config;

import com.company.platform.common.security.RestAccessDeniedHandler;
import com.company.platform.common.security.RestAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 认证中心安全配置。
 *
 * <p>存在两条过滤链：</p>
 * <ol>
 *   <li>授权服务器端点链：处理 /oauth2/authorize、/oauth2/token、JWKS、OIDC 等，
 *       仅对浏览器 HTML 请求跳转登录页，对 API 请求返回 JSON 401；</li>
 *   <li>默认链：保护登录页和其余路径，并启用表单登录。</li>
 * </ol>
 *
 * <p>两条链都显式启用 CORS，供 PC Web 从 5173 端口直接请求令牌端点。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 授权服务器专用过滤链。
     *
     * <p>关键点：使用 {@code defaultAuthenticationEntryPointFor} 而不是直接设置
     * 全局 entry point。否则 Spring Security 会把浏览器授权请求也按 JSON 401
     * 处理，导致看不到登录页。</p>
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      ObjectMapper objectMapper) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();
        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http.securityMatcher(endpointsMatcher)
                .with(authorizationServerConfigurer,
                        authorizationServer -> authorizationServer.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                .cors(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML))
                        .defaultAuthenticationEntryPointFor(
                                new RestAuthenticationEntryPoint(objectMapper),
                                new NegatedRequestMatcher(new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * 默认安全过滤链：健康检查、首页和认证服务健康接口放行，其余请求需要登录。
     *
     * <p>保留 Spring Security 生成的 HTML 登录页，供 OAuth2 授权码流程使用。
     * 非 HTML 请求（例如脚本探测）返回统一的 JSON 401。</p>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          ObjectMapper objectMapper) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/", "/api/auth/health").permitAll()
                        .anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                .csrf(Customizer.withDefaults())
                .formLogin(Customizer.withDefaults())
                .logout(Customizer.withDefaults())
                // 浏览器访问 /oauth2/authorize 时保留 Spring Security 生成的 HTML 登录页；
                // 非 HTML/API 调用返回 JSON 401。这里必须用 defaultAuthenticationEntryPointFor
                // 按媒体类型区分，不能设置全局 authenticationEntryPoint，否则登录页会被跳过。
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new RestAuthenticationEntryPoint(objectMapper),
                                new NegatedRequestMatcher(new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)));
        return http.build();
    }

    /**
     * 密码哈希器。种子密码和后续账号密码都以 BCrypt 存储，
     * 严禁在数据库保存明文或可逆加密密码。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
