package com.kellen.oa_system.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体（Spring Security 的 UserDetails 实现）。
 *
 * <p>把角色与权限码统一放进 authorities，使 {@code @PreAuthorize("hasAuthority('system:user:add')")}
 * 与 {@code hasRole('SUPER_ADMIN')} 两种写法都能生效。</p>
 *
 * <p>角色会加上 ROLE_ 前缀，这是 Spring Security 对 hasRole 的约定。</p>
 */
@Getter
public class LoginUser implements UserDetails {

    private final Long userId;

    private final String username;

    private final String password;

    /** 0禁用 1启用 2锁定 */
    private final Integer status;

    private final List<String> roles;

    private final List<String> permissions;

    public LoginUser(Long userId, String username, String password, Integer status,
                     List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.status = status;
        this.roles = roles == null ? new ArrayList<>() : roles;
        this.permissions = permissions == null ? new ArrayList<>() : permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>(roles.size() + permissions.size());
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
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

    /** 账号未过期：本项目不实现账号有效期，恒为 true */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** 账号未锁定：status 为 2 表示锁定 */
    @Override
    public boolean isAccountNonLocked() {
        return status == null || status != 2;
    }

    /** 凭证未过期：密码有效期策略暂不实现 */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 账号启用：status 为 1 才可用 */
    @Override
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
