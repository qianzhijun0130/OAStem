package com.kellen.oa_system.security;

import com.kellen.oa_system.common.redis.RedisGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * JWT 登出黑名单。
 *
 * <p>本项目 JWT 是无状态的：登出只清前端本地令牌，服务端无法撤销，令牌在有效期内仍然可用。
 * 这带来一个真实风险——用户在公共电脑上点了「退出」、或令牌被截图/日志泄露后，
 * 攻击者仍能拿它调接口直到过期（默认 8 小时）。</p>
 *
 * <p>这里用 Redis 补上这一环：登出时把令牌摘要写入黑名单，有效期正好等于令牌剩余寿命，
 * 到期自动清理，不会无限增长。鉴权过滤器每次校验前查一次（单次 EXISTS，O(1)）。</p>
 *
 * <p>两个刻意的设计：</p>
 * <ol>
 *   <li><b>只存摘要不存原文</b>——Redis 快照/监控泄露时，拿到摘要无法还原出可用令牌；</li>
 *   <li><b>Redis 故障时放行</b>——黑名单是安全增强，若因 Redis 挂掉让所有人无法登录/访问，
 *       可用性损失远大于「已登出令牌短暂可用」的风险。故障时会打 WARN，运维可感知。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "oastem:auth:blacklist:";
    /** 取 SHA-256 前 32 个十六进制字符（128 bit），碰撞概率可忽略且 key 更短 */
    private static final int FINGERPRINT_LENGTH = 32;

    private final RedisGuard redisGuard;

    /**
     * 把令牌加入黑名单。
     *
     * @param ttlSeconds 令牌剩余有效秒数；小于等于 0 表示已过期，无需登记
     */
    public void blacklist(String token, long ttlSeconds) {
        if (!StringUtils.hasText(token) || ttlSeconds <= 0) {
            return;
        }
        redisGuard.write(KEY_PREFIX + fingerprint(token), "1", Duration.ofSeconds(ttlSeconds));
        log.debug("令牌已加入黑名单，剩余 {}s", ttlSeconds);
    }

    /**
     * 令牌是否已被登出。
     *
     * <p>Redis 不可用时返回 {@code false}（放行），原因见类注释。</p>
     */
    public boolean isBlacklisted(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        return redisGuard.exists(KEY_PREFIX + fingerprint(token));
    }

    private String fingerprint(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // JDK 一定内置 SHA-256，走到这里说明运行环境异常；退回明文 key 也比让鉴权失败好
            log.error("SHA-256 不可用，黑名单改用原始令牌作为 key", e);
            return token;
        }
    }
}
