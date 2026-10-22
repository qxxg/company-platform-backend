package com.company.platform.common.web.config;

import com.company.platform.common.core.trace.TraceId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Servlet 服务统一 CORS 配置。
 *
 * <p>允许的来源由 {@code app.cors.allowed-origins} 控制，开发环境默认包含
 * PC Web 的 5173 端口。暴露 X-Request-Id，前端可以把追踪号带进错误提示。</p>
 *
 * <p>生产环境应改为精确域名，禁止继续使用宽泛来源；如果前后端同域部署，
 * 可以关闭跨域并只保留同源访问。</p>
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    /** 注册全局 CORS 规则，所有路径共用。 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(TraceId.HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
