package com.company.platform.common.security;

import com.company.platform.common.core.api.BizException;
import com.company.platform.common.core.security.PlatformClaims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Servlet 服务当前登录信息工具。
 *
 * <p>只读取 SecurityContext 中已经验签的 JWT，不回查数据库。创建人、工厂
 * 归属等审计字段必须从这里取，不能信任请求参数。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前 JWT。
     *
     * @throws BizException 401 当前请求没有认证主体
     */
    public static Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new BizException(401, "Unauthorized");
    }

    /** 当前用户 ID；客户端凭证令牌返回 null。 */
    public static Long userId() {
        Number value = currentJwt().getClaim(PlatformClaims.USER_ID);
        return value == null ? null : value.longValue();
    }

    /**
     * 是否为机器令牌（客户端凭证模式）。
     *
     * <p>客户端凭证令牌没有用户身份，只有通过 OAuth scope 显式授予的权限码。
     * 这类令牌代表“组织”而不是某个业务员，因此数据范围按全组织处理；它能读到
     * 什么完全取决于授予它的 scope，对应权限码的接口才会放行。</p>
     */
    public static boolean isServiceAccount() {
        return userId() == null;
    }

    /** 当前登录名。 */
    public static String username() {
        return currentJwt().getClaimAsString(PlatformClaims.USERNAME);
    }

    /** 当前用户显示姓名。 */
    public static String nickname() {
        return currentJwt().getClaimAsString(PlatformClaims.NICKNAME);
    }

    /** 当前用户所属工厂；总部账号可能为 null。 */
    public static Long factoryId() {
        Number value = currentJwt().getClaim(PlatformClaims.FACTORY_ID);
        return value == null ? null : value.longValue();
    }

    /** 当前令牌的客户端标识。 */
    public static String clientId() {
        return currentJwt().getClaimAsString(PlatformClaims.CLIENT_ID);
    }

    /** 当前用户角色编码列表；客户端凭证令牌没有角色时返回空列表。 */
    public static List<String> roles() {
        List<String> values = currentJwt().getClaimAsStringList(PlatformClaims.ROLES);
        return values == null ? List.of() : values;
    }

    /** 判断当前用户是否拥有任一指定角色，用于数据范围等业务判断。 */
    public static boolean hasAnyRole(String... roleCodes) {
        List<String> current = roles();
        for (String roleCode : roleCodes) {
            if (current.contains(roleCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前令牌是否拥有指定权限码。
     *
     * <p>用于“按权限隐藏敏感字段”这类展示控制，例如工单材料成本只对有成本
     * 权限的角色返回。真正的数据安全仍由接口上的 {@code @PreAuthorize} 保证，
     * 这里只是避免把内部成本暴露给无关角色。</p>
     */
    public static boolean hasPermission(String permission) {
        List<String> current = currentJwt().getClaimAsStringList(PlatformClaims.PERMISSIONS);
        return current != null && current.contains(permission);
    }
}
