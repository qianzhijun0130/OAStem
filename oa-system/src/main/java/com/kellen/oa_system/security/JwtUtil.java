package com.kellen.oa_system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 签发与解析工具。
 *
 * <p>设计说明：令牌中携带 userId / 角色 / 权限码，使鉴权过滤器无需回查数据库，
 * 保持服务无状态。代价是权限变更后需要用户重新登录才能生效——
 * 这与需求中「权限变更后用户重新登录即更新」的约定一致。</p>
 *
 * <p>安全约束：密钥长度必须 >= 32 字节，否则 HS256 签名会抛异常；
 * 密钥来自配置，禁止硬编码在源码里。</p>
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMS = "perms";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(LoginUser loginUser) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(loginUser.getUsername())
                .claim(CLAIM_USER_ID, loginUser.getUserId())
                .claim(CLAIM_ROLES, loginUser.getRoles())
                .claim(CLAIM_PERMS, loginUser.getPermissions())
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析令牌。任何签名错误、过期、格式错误都会抛出 JwtException，由调用方处理。
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        Object value = claims.get(CLAIM_ROLES);
        return value instanceof List ? (List<String>) value : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(Claims claims) {
        Object value = claims.get(CLAIM_PERMS);
        return value instanceof List ? (List<String>) value : List.of();
    }

    public Long getUserId(Claims claims) {
        Object value = claims.get(CLAIM_USER_ID);
        if (value instanceof Integer integer) {
            return integer.longValue();
        }
        return value instanceof Long l ? l : null;
    }

    public Long getExpirationMillis() {
        return expiration;
    }

    /**
     * 令牌剩余有效秒数，用于给「登出黑名单」设置 TTL——黑名单只需要活到令牌自然过期为止。
     *
     * @return 剩余秒数；已过期或无法解析返回 0
     */
    public long getRemainingSeconds(Claims claims) {
        Date expireAt = claims == null ? null : claims.getExpiration();
        if (expireAt == null) {
            return 0L;
        }
        long remain = expireAt.getTime() - System.currentTimeMillis();
        return remain <= 0 ? 0L : remain / 1000;
    }
}
