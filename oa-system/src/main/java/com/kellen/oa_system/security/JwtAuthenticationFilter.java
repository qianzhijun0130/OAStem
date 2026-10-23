package com.kellen.oa_system.security;

import com.kellen.oa_system.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器。
 *
 * <p>在每个请求到达 Controller 之前，把 Bearer 令牌还原为已认证的 LoginUser 并写入
 * SecurityContext。令牌无效时不做拦截、也不抛异常，而是清空上下文并放行——
 * 最终由 AuthenticationEntryPoint / AccessDeniedHandler 统一返回 401 / 403，
 * 这样错误响应格式才能与全局异常处理保持一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 鉴权失败时把具体错误码放进 request attribute，由 SecurityConfig 的
     * AuthenticationEntryPoint 取出来写响应。
     *
     * <p>为什么不在这里直接写响应：令牌无效与权限不足的响应格式必须与全局异常处理一致，
     * 统一由 entry point 输出才不会出现两套 JSON 结构。</p>
     */
    public static final String AUTH_ERROR_ATTRIBUTE = "OA_AUTH_ERROR_CODE";

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final OnlineUserRegistry onlineUserRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                Long userId = jwtUtil.getUserId(claims);
                // 登出后的令牌签名依然有效，必须查一次黑名单，否则「退出登录」只是前端删本地存储
                if (tokenBlacklistService.isBlacklisted(token)) {
                    log.debug("令牌已登出（黑名单命中），不建立认证上下文");
                    SecurityContextHolder.clearContext();
                } else if (onlineUserRegistry.isKicked(userId, jwtUtil.getIssuedAtMillis(claims))) {
                    // 管理员强制下线：只让「踢出时刻之前签发」的令牌失效，重新登录拿到的新令牌不受影响
                    log.info("令牌已被管理员强制下线，拒绝认证 userId={}", userId);
                    request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.TOKEN_KICKED);
                    SecurityContextHolder.clearContext();
                } else {
                    List<String> roles = jwtUtil.getRoles(claims);
                    List<String> permissions = jwtUtil.getPermissions(claims);

                    // 令牌已通过签名校验，视作有效用户；密码不再需要，置为空串避免残留
                    LoginUser loginUser = new LoginUser(userId, claims.getSubject(), "", 1, roles, permissions);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("令牌解析失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
