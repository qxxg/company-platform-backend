package com.company.platform.auth.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 平台用户加载服务。
 *
 * <p>登录时从认证库读取用户、角色和权限。返回标准 Spring Security
 * {@link User} 是为了让 JDBC 授权服务可以直接序列化；更完整的资料由
 * {@link #loadPrincipalByUsername(String)} 在签发 JWT 时再次加载。</p>
 */
@Service
public class PlatformUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public PlatformUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 登录认证只返回标准 {@link User}，由 JDBC 授权服务写入授权记录。
     *
     * <p>标准 User 可以被 Spring Security 的 JDBC 授权服务直接序列化；
     * 更完整的平台资料在签发 JWT 时通过
     * {@link #loadPrincipalByUsername(String)} 再次加载。</p>
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        PlatformUserPrincipal principal = loadPrincipalByUsername(username);
        return User.withUsername(principal.getUsername())
                .password(principal.getPassword())
                .authorities(principal.getAuthorities())
                .disabled(!principal.isEnabled())
                .build();
    }

    /**
     * 加载平台完整用户资料。
     *
     * <p>查询顺序：用户主表 → 启用角色 → 启用权限。角色和权限都使用
     * LinkedHashSet 保存，保证 JWT 中的顺序稳定，便于比对与测试。</p>
     *
     * @throws UsernameNotFoundException 用户不存在
     */
    public PlatformUserPrincipal loadPrincipalByUsername(String username) throws UsernameNotFoundException {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, username, password_hash, nickname, employee_no, factory_id, status "
                        + "FROM sys_user WHERE username = ?",
                username);

        if (rows.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        Map<String, Object> row = rows.get(0);
        Long userId = number(row.get("id"));
        Long factoryId = number(row.get("factory_id"));
        int status = row.get("status") == null ? 0 : number(row.get("status")).intValue();

        List<String> roles = jdbcTemplate.queryForList(
                "SELECT r.code FROM sys_role r "
                        + "JOIN sys_user_role ur ON ur.role_id = r.id "
                        + "WHERE ur.user_id = ? AND r.status = 1",
                String.class, userId);

        List<String> permissions = jdbcTemplate.queryForList(
                "SELECT DISTINCT p.code FROM sys_permission p "
                        + "JOIN sys_role_permission rp ON rp.permission_id = p.id "
                        + "JOIN sys_user_role ur ON ur.role_id = rp.role_id "
                        + "WHERE ur.user_id = ? AND p.status = 1",
                String.class, userId);

        return new PlatformUserPrincipal(
                userId,
                (String) row.get("username"),
                (String) row.get("password_hash"),
                (String) row.get("nickname"),
                (String) row.get("employee_no"),
                factoryId,
                status == 1,
                new LinkedHashSet<>(roles),
                new LinkedHashSet<>(permissions));
    }

    /** JDBC 驱动可能返回 Integer/Long/BigInteger，统一转换为 Long。 */
    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
