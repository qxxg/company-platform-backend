package com.company.platform.auth.web;

import com.company.platform.common.core.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证中心公开入口。
 *
 * <p>只暴露服务自检和 OIDC 发现地址，方便部署检查；不返回任何用户或密钥信息。</p>
 */
@RestController
public class HomeController {

    private final String issuer;

    public HomeController(@Value("${app.auth.issuer}") String issuer) {
        this.issuer = issuer;
    }

    /** 返回认证中心运行信息和常用端点，便于开发联调。 */
    @GetMapping("/")
    public ApiResponse<Map<String, Object>> home() {
        return ApiResponse.ok(Map.of(
                "service", "platform-auth",
                "issuer", issuer,
                "login", issuer + "/login",
                "discovery", issuer + "/.well-known/openid-configuration"
        ));
    }

    /** 认证服务健康检查。 */
    @GetMapping("/api/auth/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", "platform-auth"));
    }
}
