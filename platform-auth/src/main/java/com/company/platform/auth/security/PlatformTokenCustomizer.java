package com.company.platform.auth.security;

import com.company.platform.common.core.security.PlatformClaims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JWT 平台声明定制器。
 *
 * <p>认证中心默认只写入 sub、scope、iss 等标准声明。本类补充平台需要的
 * userId、username、nickname、employeeNo、factoryId、roles、permissions，
 * 让网关和业务服务无需回查数据库即可完成粗粒度授权。</p>
 *
 * <p>两种主体都要兼容：</p>
 * <ul>
 *   <li>用户授权码流程：principal 是 {@link PlatformUserPrincipal} 或 JDBC
 *       授权记录反序列化后的标准 {@code User}；</li>
 *   <li>客户端凭证流程：principal 是 {@code RegisteredClient}，只按 scope
 *       映射权限，不写入用户信息。</li>
 * </ul>
 */
@Component
public class PlatformTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final PlatformUserDetailsService userDetailsService;

    public PlatformTokenCustomizer(PlatformUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * scope 到权限码的映射，仅用于客户端凭证令牌。
     *
     * <p>用户令牌不经过这里，而是直接使用数据库中的角色权限，避免 scope 过大导致
     * 普通用户越权。新增业务模块时，应同时维护 OAuth2 客户端 scope 和此映射。</p>
     */
    private static final Map<String, List<String>> SCOPE_PERMISSIONS = Map.ofEntries(
            Map.entry("system.read", List.of("system:user:list", "system:factory:list")),
            Map.entry("system.write", List.of("system:user:create")),
            Map.entry("order.read", List.of("order:read")),
            Map.entry("order.write", List.of("order:create")),
            Map.entry("order.approve", List.of("order:approve")),
            Map.entry("finance.read", List.of("finance:contract:read", "finance:credit:read",
                    "finance:receivable:read", "finance:receipt:read",
                    "finance:invoice:read", "finance:statement:read",
                    "finance:expense:read", "finance:loan:read")),
            Map.entry("finance.write", List.of("finance:contract:write", "finance:receivable:write",
                    "finance:receipt:write", "finance:invoice:write", "finance:statement:write",
                    "finance:expense:write", "finance:expense:pay",
                    "finance:loan:write", "finance:loan:disburse", "finance:loan:repay")),
            Map.entry("customer.read", List.of("customer:read")),
            Map.entry("customer.write", List.of("customer:read", "customer:write")),
            Map.entry("production.read", List.of("equipment:read", "production:schedule:read")),
            Map.entry("production.write", List.of("equipment:write", "production:schedule:write",
                    "production:queue:jump:apply")),
            Map.entry("production.approve", List.of("production:queue:jump:approve")),
            Map.entry("equipment.maintenance", List.of("equipment:maintenance:read",
                    "equipment:maintenance:write", "equipment:inspection:read",
                    "equipment:inspection:write")),
            Map.entry("warehouse.read", List.of("warehouse:read")),
            Map.entry("warehouse.write", List.of("warehouse:manage", "warehouse:inbound",
                    "warehouse:outbound")),
            Map.entry("approval.read", List.of("approval:read")),
            Map.entry("approval.submit", List.of("approval:read", "approval:submit")),
            Map.entry("approval.approve", List.of("approval:approve", "approval:apply")),
            Map.entry("approval.manage", List.of("approval:manage")),
            Map.entry("ai.use", List.of("ai:assistant:use")),
            Map.entry("bi.read", List.of("bi:dashboard:read", "bi:cockpit:read"))
    );

    /**
     * 只定制 access_token；ID Token 和刷新令牌保持标准声明。
     */
    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.getValue().equals(context.getTokenType().getValue())) {
            return;
        }

        Authentication principal = context.getPrincipal();
        if (principal.getPrincipal() instanceof PlatformUserPrincipal user) {
            applyUserClaims(context, user);
            return;
        }
        if (principal.getPrincipal() instanceof User user) {
            // JDBC 授权记录里保存的是标准 User，只有用户名和权限；
            // 签发令牌时按用户名重新加载平台资料，补齐昵称、工号、工厂等声明。
            applyUserClaims(context, userDetailsService.loadPrincipalByUsername(user.getUsername()));
            return;
        }

        RegisteredClient client = resolveClient(principal);
        if (client != null) {
            context.getClaims().claim(PlatformClaims.CLIENT_ID, client.getClientId());
            context.getClaims().claim(PlatformClaims.USERNAME, client.getClientId());
            context.getClaims().claim(PlatformClaims.ROLES, List.of());
            context.getClaims().claim(PlatformClaims.PERMISSIONS,
                    permissionsForScopes(context.getAuthorizedScopes()));
        }
    }

    /**
     * 把平台用户信息写入 JWT 声明。空字段不写入，避免产生无意义的 null 声明。
     */
    private static void applyUserClaims(JwtEncodingContext context, PlatformUserPrincipal user) {
        context.getClaims().claim(PlatformClaims.USER_ID, user.getId());
        context.getClaims().claim(PlatformClaims.USERNAME, user.getUsername());
        if (user.getNickname() != null) {
            context.getClaims().claim(PlatformClaims.NICKNAME, user.getNickname());
        }
        if (user.getEmployeeNo() != null) {
            context.getClaims().claim(PlatformClaims.EMPLOYEE_NO, user.getEmployeeNo());
        }
        if (user.getFactoryId() != null) {
            context.getClaims().claim(PlatformClaims.FACTORY_ID, user.getFactoryId());
        }
        context.getClaims().claim(PlatformClaims.ROLES, new ArrayList<>(user.getRoles()));
        context.getClaims().claim(PlatformClaims.PERMISSIONS, new ArrayList<>(user.getPermissions()));
    }

    /**
     * 从客户端凭证认证主体中取回 RegisteredClient。
     * Spring Authorization Server 在不同阶段可能暴露不同包装类型，这里都兼容。
     */
    private static RegisteredClient resolveClient(Authentication principal) {
        if (principal.getPrincipal() instanceof RegisteredClient client) {
            return client;
        }
        if (principal instanceof OAuth2ClientAuthenticationToken clientAuthentication) {
            return clientAuthentication.getRegisteredClient();
        }
        return null;
    }

    /** 将客户端凭证令牌的 scope 集合展开为权限码，去重并保留顺序。 */
    private static List<String> permissionsForScopes(Set<String> scopes) {
        Set<String> permissions = new LinkedHashSet<>();
        if (scopes != null) {
            for (String scope : scopes) {
                permissions.addAll(SCOPE_PERMISSIONS.getOrDefault(scope, List.of()));
            }
        }
        return new ArrayList<>(permissions);
    }
}
