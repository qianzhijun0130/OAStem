package com.kellen.oa_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 *
 * <p>单独抽离，避免与 SecurityConfig 形成循环依赖：
 * SecurityConfig 依赖 UserDetailsServiceImpl → UserService → PasswordEncoder，
 * 若 PasswordEncoder 也定义在 SecurityConfig 中，就会构成环。</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
