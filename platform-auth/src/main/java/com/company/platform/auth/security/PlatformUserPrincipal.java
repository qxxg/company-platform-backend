package com.company.platform.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 平台登录主体。
 *
 * <p>在 Spring Security 标准字段之外保存员工编号、所属工厂、角色和权限。
 * 密码字段只在登录校验阶段使用，不会写入 JWT。</p>
 */
public class PlatformUserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户主键，写入 JWT 的 user_id 声明。 */
    private final Long id;
    /** 登录名，写入 username 声明。 */
    private final String username;
    /** BCrypt 密码哈希，只用于 AuthenticationManager 校验。 */
    private final String password;
    /** 显示姓名，可为空。 */
    private final String nickname;
    /** 员工编号，可为空。 */
    private final String employeeNo;
    /** 所属工厂；总部账号可为空。 */
    private final Long factoryId;
    /** 账号是否启用。 */
    private final boolean enabled;
    /** 角色编码，例如 ADMIN、SALES、FINANCE。 */
    private final Set<String> roles;
    /** 权限码，例如 order:read、order:create。 */
    private final Set<String> permissions;

    public PlatformUserPrincipal(Long id, String username, String password, String nickname, String employeeNo,
                                 Long factoryId, boolean enabled, Set<String> roles, Set<String> permissions) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.employeeNo = employeeNo;
        this.factoryId = factoryId;
        this.enabled = enabled;
        this.roles = Set.copyOf(roles);
        this.permissions = Set.copyOf(permissions);
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * 将角色与权限转换为 Spring Security 权限。
     *
     * <p>角色统一加 {@code ROLE_} 前缀，权限码保持原样。例如 ADMIN 变为
     * ROLE_ADMIN，order:read 保持 order:read，分别对应 hasRole 和 hasAuthority。</p>
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
