package com.company.platform.system.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.core.security.PlatformClaims;
import com.company.platform.system.entity.SysFactory;
import com.company.platform.system.entity.SysUser;
import com.company.platform.system.service.SystemQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统基础数据接口。
 *
 * <p>当前提供当前用户信息、用户列表和工厂列表。返回体不包含密码哈希；
 * 用户与工厂的写操作要在权限模型、审计和员工档案完善后再开放。</p>
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemQueryService systemQueryService;

    public SystemController(SystemQueryService systemQueryService) {
        this.systemQueryService = systemQueryService;
    }

    /** 系统服务健康检查。 */
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", "platform-system"));
    }

    /**
     * 返回当前 JWT 的核心声明，供前端开发联调和权限展示。
     *
     * <p>这里读取的是已经验签的 JWT，不会回查数据库；因此角色权限变更后，
     * 已签发令牌要等到过期或重新登录才会更新。</p>
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subject", jwt.getSubject());
        result.put("userId", claim(jwt, PlatformClaims.USER_ID));
        result.put("username", claim(jwt, PlatformClaims.USERNAME));
        result.put("roles", jwt.getClaimAsStringList(PlatformClaims.ROLES));
        result.put("permissions", jwt.getClaimAsStringList(PlatformClaims.PERMISSIONS));
        return ApiResponse.ok(result);
    }

    /** 用户列表，需要 {@code system:user:list} 权限；不返回密码字段。 */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<List<UserView>> users() {
        return ApiResponse.ok(systemQueryService.listUsers().stream().map(UserView::from).toList());
    }

    /** 工厂列表，需要 {@code system:factory:list} 权限。 */
    @GetMapping("/factories")
    @PreAuthorize("hasAuthority('system:factory:list')")
    public ApiResponse<List<FactoryView>> factories() {
        return ApiResponse.ok(systemQueryService.listFactories().stream().map(FactoryView::from).toList());
    }

    /** 读取 JWT 声明，缺失时返回空字符串而不是 null，简化前端展示。 */
    private static Object claim(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);
        return value == null ? "" : value;
    }

    public record UserView(Long id, String username, String nickname, String employeeNo, Long factoryId,
                           Integer status, LocalDateTime createdAt) {
        public static UserView from(SysUser user) {
            return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getEmployeeNo(),
                    user.getFactoryId(), user.getStatus(), user.getCreatedAt());
        }
    }

    public record FactoryView(Long id, String code, String name, String address, Integer status) {
        public static FactoryView from(SysFactory factory) {
            return new FactoryView(factory.getId(), factory.getCode(), factory.getName(), factory.getAddress(),
                    factory.getStatus());
        }
    }
}
