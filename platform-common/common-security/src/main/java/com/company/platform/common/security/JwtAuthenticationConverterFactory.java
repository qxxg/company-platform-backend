package com.company.platform.common.security;

import com.company.platform.common.core.security.PlatformClaims;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * JWT 权限声明转换工厂。
 *
 * <p>认证中心把角色写入 roles、权限码写入 permissions。这里把两者合并为
 * Spring Security 的 GrantedAuthority 集合：</p>
 *
 * <ul>
 *   <li>permissions 保持原样，例如 order:read；</li>
 *   <li>roles 增加 ROLE_ 前缀，例如 SALES → ROLE_SALES。</li>
 * </ul>
 *
 * <p>合并时使用 LinkedHashSet 去重，避免同一权限同时来自角色和 scope。</p>
 */
public final class JwtAuthenticationConverterFactory {

    private JwtAuthenticationConverterFactory() {
    }

    /**
     * 创建 Servlet 资源服务器使用的转换器。
     *
     * <p>Maps the "permissions" claim to raw authorities and "roles" to ROLE_* authorities.</p>
     */
    public static JwtAuthenticationConverter create() {
        JwtGrantedAuthoritiesConverter permissionsConverter = new JwtGrantedAuthoritiesConverter();
        permissionsConverter.setAuthoritiesClaimName(PlatformClaims.PERMISSIONS);
        permissionsConverter.setAuthorityPrefix("");

        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName(PlatformClaims.ROLES);
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new LinkedHashSet<>();
            authorities.addAll(permissionsConverter.convert(jwt));
            authorities.addAll(rolesConverter.convert(jwt));
            return authorities;
        });
        return converter;
    }

    /**
     * 将字符串声明列表转换为权限集合，自动忽略 null 和空白值。
     *
     * <p>网关的响应式转换器也会复用该逻辑，保证 Servlet 与 WebFlux 两侧
     * 权限映射规则一致。</p>
     *
     * @param values 声明值，例如 ["order:read"]
     * @param prefix 权限前缀，角色传 "ROLE_"，权限码传 ""
     */
    public static Collection<GrantedAuthority> authorities(List<String> values, String prefix) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(prefix + value));
                }
            }
        }
        return authorities;
    }
}
