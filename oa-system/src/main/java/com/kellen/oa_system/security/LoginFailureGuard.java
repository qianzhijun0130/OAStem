package com.kellen.oa_system.security;

import com.kellen.oa_system.common.redis.RedisGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 登录失败限流（防暴力破解）。
 *
 * <p>规则：同一账号在 {@code security.login.failure-window-seconds} 窗口内失败达到
 * {@code security.login.max-failures} 次，锁定 {@code security.login.lock-seconds} 秒。
 * 登录成功立即清零，避免正常用户偶尔输错被累积。</p>
 *
 * <p>为什么按账号而不是按 IP：攻击者换 IP 很容易，按 IP 限流既能被绕过又会误伤
 * 同一出口 NAT 后的正常用户；账号维度则直接保护了被攻击的目标本身。</p>
 *
 * <p>Redis 不可用时的行为：{@code increment} 返回 -1，此处**不做任何锁定**（放行）。
 * 宁可短暂失去限流，也不能因为缓存故障把所有人挡在登录页外。</p>
 */
@Slf4j
@Service
public class LoginFailureGuard {

    private static final String FAIL_KEY_PREFIX = "oastem:auth:login:fail:";
    private static final String LOCK_KEY_PREFIX = "oastem:auth:login:lock:";

    private final RedisGuard redisGuard;
    private final int maxFailures;
    private final long failureWindowSeconds;
    private final long lockSeconds;

    public LoginFailureGuard(RedisGuard redisGuard,
                             @Value("${security.login.max-failures:5}") int maxFailures,
                             @Value("${security.login.failure-window-seconds:300}") long failureWindowSeconds,
                             @Value("${security.login.lock-seconds:300}") long lockSeconds) {
        this.redisGuard = redisGuard;
        this.maxFailures = maxFailures;
        this.failureWindowSeconds = failureWindowSeconds;
        this.lockSeconds = lockSeconds;
    }

    /** 账号当前是否处于限流锁定中；Redis 不可用时返回 false（放行） */
    public boolean isLocked(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return redisGuard.exists(LOCK_KEY_PREFIX + username);
    }

    /** 记录一次登录失败；达到阈值时加锁 */
    public void onFailure(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        long count = redisGuard.increment(FAIL_KEY_PREFIX + username, Duration.ofSeconds(failureWindowSeconds));
        // -1 表示 Redis 不可用、无法计数：此时既不累加也不锁定
        if (count > 0 && count >= maxFailures) {
            redisGuard.write(LOCK_KEY_PREFIX + username, "1", Duration.ofSeconds(lockSeconds));
            log.warn("账号 {} 连续登录失败 {} 次，已临时锁定 {}s", username, count, lockSeconds);
        }
    }

    /** 登录成功：清空失败计数与锁定标记 */
    public void onSuccess(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        redisGuard.remove(FAIL_KEY_PREFIX + username);
        redisGuard.remove(LOCK_KEY_PREFIX + username);
    }
}
