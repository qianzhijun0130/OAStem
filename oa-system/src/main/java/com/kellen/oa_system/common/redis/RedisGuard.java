package com.kellen.oa_system.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Redis 访问的统一入口，负责「失败降级」。
 *
 * <p>为什么需要它：缓存与登出黑名单都是**增强能力**，不是业务主链路。
 * 若 Redis 宕机就让接口 500，等于把一个可选组件变成了单点故障。这里的约定是——</p>
 * <ul>
 *   <li>{@link #get} / {@link #read} 失败返回 {@code null}，调用方回源数据库；</li>
 *   <li>{@link #exists} 失败返回 {@code false}（视为无黑名单、无锁定）；</li>
 *   <li>{@link #increment} 失败返回 {@code -1}，调用方必须当作「无法计数」而不是「计数为零」；</li>
 *   <li>写失败只记日志，绝不让业务报错。</li>
 * </ul>
 *
 * <p>另一层保护是**熔断冷却**：Redis 连接超时设为 3s，若每个请求都去撞一次，
 * 一个挂掉的 Redis 会让全站请求各慢 3s。因此失败后进入 {@value #COOLDOWN_MS}ms 冷却期，
 * 期间直接降级、不再尝试；冷却结束自动半开重试，Redis 恢复后无需重启应用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisGuard {

    /** 失败后的冷却时长：期间直接降级，避免每个请求都付出连接超时代价 */
    private static final long COOLDOWN_MS = 30_000L;
    /** 告警节流：Redis 长时间不可用时每分钟只打一条 WARN，避免刷爆日志 */
    private static final long WARN_INTERVAL_MS = 60_000L;
    /** SCAN 单批数量 */
    private static final long SCAN_BATCH = 200L;

    private final StringRedisTemplate redisTemplate;

    /** 降级截止时间戳（毫秒）；小于等于当前时间表示正常 */
    private final AtomicLong downUntil = new AtomicLong(0L);
    private final AtomicLong lastWarnAt = new AtomicLong(0L);

    /**
     * 读取并反序列化。命中缓存返回值；未命中、Redis 不可用、反序列化失败均返回 null。
     *
     * @param parser 把 JSON 字符串还原为对象；解析异常同样视为未命中（脏数据自动失效）
     */
    public <T> T read(String key, Function<String, T> parser) {
        String raw = get(key);
        if (raw == null) {
            return null;
        }
        try {
            return parser.apply(raw);
        } catch (RuntimeException e) {
            log.warn("缓存反序列化失败，按未命中处理, key={}", key, e);
            remove(key);
            return null;
        }
    }

    /** 读取原始字符串；Redis 不可用返回 null */
    public String get(String key) {
        if (!acquire()) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            markDown(e);
            return null;
        }
    }

    /** 写入并设置过期时间；ttl 为 null 表示不过期（慎用） */
    public void write(String key, String value, Duration ttl) {
        if (!acquire()) {
            return;
        }
        try {
            if (ttl == null) {
                redisTemplate.opsForValue().set(key, value);
            } else {
                redisTemplate.opsForValue().set(key, value, ttl);
            }
        } catch (RuntimeException e) {
            markDown(e);
        }
    }

    /** key 是否存在；Redis 不可用返回 false（等价于「没有黑名单 / 没有锁定」） */
    public boolean exists(String key) {
        if (!acquire()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RuntimeException e) {
            markDown(e);
            return false;
        }
    }

    /** 删除单个 key；返回是否成功执行（不代表原本存在） */
    public boolean remove(String key) {
        if (!acquire()) {
            return false;
        }
        try {
            redisTemplate.delete(key);
            return true;
        } catch (RuntimeException e) {
            markDown(e);
            return false;
        }
    }

    /**
     * 自增计数（首次自增时设置过期）。
     *
     * <p>Redis 不可用时返回 {@code -1}，调用方必须把它当作「无法计数」而不是「计数为零」，
     * 否则登录限流会在 Redis 故障时误锁或误放。</p>
     */
    public long increment(String key, Duration ttl) {
        if (!acquire()) {
            return -1L;
        }
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value == null) {
                return -1L;
            }
            // 只在第一次自增时设置过期，避免每次请求都刷新窗口导致计数永不过期
            if (value == 1L && ttl != null) {
                redisTemplate.expire(key, ttl);
            }
            return value;
        } catch (RuntimeException e) {
            markDown(e);
            return -1L;
        }
    }

    /**
     * 按前缀批量删除。
     *
     * <p>用 SCAN 而不是 KEYS：KEYS 会阻塞 Redis 单线程，库大时可能是秒级卡顿。
     * 这里不保证原子性——缓存清理允许「少删」，下次过期自然会好。</p>
     */
    public void removeByPrefix(String prefix) {
        if (!acquire()) {
            return;
        }
        try {
            ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(SCAN_BATCH).build();
            List<String> keys = new ArrayList<>();
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return null;
            }, true);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("清理缓存 {} 个, prefix={}", keys.size(), prefix);
            }
        } catch (RuntimeException e) {
            markDown(e);
        }
    }

    /**
     * 连通性探针：不受冷却期影响，用于启动检查与运维排查。
     *
     * @return 连通返回如 {@code PONG | redis 3.0.504}，失败返回 null
     */
    public String ping() {
        try {
            return redisTemplate.execute((RedisCallback<String>) connection -> {
                String pong = connection.ping();
                String version = resolveVersion(connection);
                return version == null ? pong : pong + " | redis " + version;
            }, true);
        } catch (RuntimeException e) {
            markDown(e);
            return null;
        }
    }

    /** 当前是否处于可用状态（冷却期内视为不可用） */
    public boolean isAvailable() {
        return System.currentTimeMillis() >= downUntil.get();
    }

    // ---------- 内部方法 ----------

    private boolean acquire() {
        return System.currentTimeMillis() >= downUntil.get();
    }

    private void markDown(RuntimeException e) {
        long now = System.currentTimeMillis();
        downUntil.set(now + COOLDOWN_MS);
        if (now - lastWarnAt.get() >= WARN_INTERVAL_MS) {
            lastWarnAt.set(now);
            String reason;
            if (e instanceof DataAccessException dataAccessException) {
                reason = dataAccessException.getMostSpecificCause().getMessage();
            } else {
                reason = e.getMessage();
            }
            log.warn("Redis 不可用，接下来 {}s 内相关功能降级到数据库（不影响主流程）：{}",
                    COOLDOWN_MS / 1000, reason);
        }
    }

    /** 版本号只是排查用的辅助信息，取不到不影响 ping 结论 */
    private String resolveVersion(RedisConnection connection) {
        try {
            Properties info = connection.serverCommands().info("server");
            return info == null ? null : info.getProperty("redis_version");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
