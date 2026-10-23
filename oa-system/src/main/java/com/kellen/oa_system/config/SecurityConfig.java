package com.kellen.oa_system.config;

import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.security.JwtAuthenticationFilter;
import com.kellen.oa_system.security.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Security 配置。
 *
 * <p>要点：
 * <ol>
 *   <li>前后端分离 + JWT，关闭 CSRF 与 Session（STATELESS）；</li>
 *   <li>未认证/无权限时返回与业务接口一致的 JSON 结构，而不是默认的登录页跳转；</li>
 *   <li>{@code @EnableMethodSecurity} 开启方法级鉴权，配合 {@code @PreAuthorize} 使用。</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 无需认证即可访问的路径 */
    private static final String[] PERMIT_ALL_PATTERNS = {
            "/auth/login",
            "/error",
            // 头像等公开图片由静态资源映射直接返回，请求不会带 Authorization 头，
            // 若不在此放行会被 anyRequest().authenticated() 拦成 401
            "/uploads/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 过滤器可能已标记具体原因（如被管理员强制下线），没有则回落到通用 401
                            Object reason = request.getAttribute(
                                    com.kellen.oa_system.security.JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
                            writeJson(response,
                                    reason instanceof ErrorCode code ? code : ErrorCode.UNAUTHORIZED);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 使用 DaoAuthenticationProvider 显式装配，避免 Spring Security 在存在多个
     * UserDetailsService 时因自动装配歧义而启动失败。
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 仅放开本地前端开发端口，生产环境应改为实际域名
        configuration.setAllowedOriginPatterns(List.of("http://localhost:3000", "http://127.0.0.1:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 直接写 JSON 字符串而不注入 ObjectMapper。
     *
     * <p>原因：Spring Boot 4 默认使用 Jackson 3（groupId 已改为 tools.jackson），
     * 若按 Jackson 2 的 com.fasterxml.jackson.databind.ObjectMapper 注入，
     * 编译期能过（依赖树里同时存在 2.x），运行时却拿不到 Bean 而抛 NoClassDefFoundError。
     * 这里输出的错误结构固定且不含用户输入，手写拼接即可，避免与 JSON 版本耦合。</p>
     */
    private void writeJson(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode == ErrorCode.FORBIDDEN
                ? HttpServletResponse.SC_FORBIDDEN
                : HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = String.format(
                "{\"code\":%d,\"message\":\"%s\",\"data\":null,\"timestamp\":\"%s\"}",
                errorCode.getCode(),
                errorCode.getMessage(),
                LocalDateTime.now());
        response.getWriter().write(body);
        response.getWriter().flush();
    }
}
