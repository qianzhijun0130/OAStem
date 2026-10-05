package com.kellen.oa_system.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.kellen.oa_system.system.service.UserService;

/**
 * 桥接 Spring Security 与自有用户体系。
 *
 * <p>登录时由 DaoAuthenticationProvider 调用此方法获取用户，
 * 密码比对交给 PasswordEncoder，业务代码不参与明文比较。</p>
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userService.loadLoginUser(username);
    }
}
