package com.company.platform.common.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 允许来源配置。
 *
 * <p>默认值用于本地开发：5173 是 Vite 前端，3000/8080 保留给其他调试工具。
 * 内网开发机 192.168.3.43 也显式加入，方便用手机或另一台电脑访问。</p>
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** 允许的 Origin 列表，支持通配符模式（如 https://*.example.com）。 */
    private List<String> allowedOrigins = List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://192.168.3.43:5173",
            "http://localhost:3000",
            "http://localhost:8080"
    );

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
