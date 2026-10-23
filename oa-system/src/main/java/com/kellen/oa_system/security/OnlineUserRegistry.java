package com.kellen.oa_system.security;

import com.kellen.oa_system.common.redis.RedisGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 在线会话注册表（Redis）。
 *
 * <p><b>为什么 JWT 无状态还要这份注册表</b>：无状态令牌解决了「每次请求查库」的性能问题，
 * 但代价是服务端不知道「现在谁在线」、也没法让某个人的令牌立刻作废。
 * 这里用两份 Redis 数据补上这个缺口，且都只是**旁路**信息：</p>
 * <ul>
 *   <li>{@code oastem:auth:online:{userId}} → 会话快照（登录时写入，TTL = 令牌有效期）。
 *       过期即代表令牌到期，无需额外清理任务；</li>
 *   <li>{@code oastem:auth:kicked:{userId}} → 强制下线时刻（毫秒）。
 *       鉴权时比较「令牌签发时间 &lt; 踢出时刻」，是则视为已失效。</li>
 * </ul>
 *
 * <p><b>为什么用「踢出时刻」而不是把令牌加黑名单</b>：服务端只拿到令牌，不保存令牌本身，
 * 一个用户可能有多个（多端登录）。记时刻可以一次性让该用户<b>所有旧令牌</b>失效，
 * 而重新登录拿到的新令牌签发时间更晚，自然不受影响。</p>
 *
 * <p><b>降级约定</b>：Redis 不可用时列表返回空、踢出标记写不进去。前者只是「看不到谁在线」，
 * 后者必须<b>如实报错</b>——否则管理员会以为用户已被踢下线，实际令牌仍然有效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserRegistry {

    private static final String ONLINE_PREFIX = "oastem:auth:online:";
    private static final String KICKED_PREFIX = "oastem:auth:kicked:";
    /**
     * 踢出标记的最短保留时长。
     *
     * <p>正常情况下 TTL = 令牌剩余寿命；但「用户当时不在线」时拿不到剩余寿命，
     * 此时仍要留一段时间，防止把「挂着旧令牌、稍后再发请求」的场景漏掉。
     * 取 7 天，与默认令牌有效期同量级。</p>
     */
    private static final Duration KICK_RETENTION = Duration.ofDays(7);

    private final RedisGuard redisGuard;
    private final ObjectMapper objectMapper;

    /** 会话快照（存进 Redis 的结构，字段与前端展示一一对应） */
    public record Session(Long userId, String username, String realName,
                          String clientIp, String userAgent,
                          long loginAtMillis, long expireAtMillis) {
    }

    /**
     * 登记一次登录。
     *
     * <p>同一用户重复登录会覆盖前一条（不区分设备）。这是有意的简化：
     * 本项目按「一人一会话」处理，踢出也是按人踢，保留多条只会让列表出现重复行。</p>
     */
    public void register(Long userId, String username, String realName,
                         String clientIp, String userAgent, long ttlSeconds) {
        if (userId == null || ttlSeconds <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Session session = new Session(userId, username, realName, clientIp, truncate(userAgent),
                now, now + ttlSeconds * 1000);
        try {
            redisGuard.write(ONLINE_PREFIX + userId, objectMapper.writeValueAsString(session),
                    Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException e) {
            // 序列化/Redis 异常都不应影响登录本身：在线列表是旁路能力
            log.warn("登记在线会话失败 userId={}", userId, e);
        }
    }

    /** 登出时移除会话快照（踢出标记不清理：它按自身 TTL 过期即可） */
    public void unregister(Long userId) {
        if (userId != null) {
            redisGuard.remove(ONLINE_PREFIX + userId);
        }
    }

    /** 某用户当前是否有活跃会话（用于「踢一个已经自己登出的用户」时给出准确提示） */
    public boolean hasSession(Long userId) {
        return userId != null && redisGuard.exists(ONLINE_PREFIX + userId);
    }

    /** 当前在线用户列表；Redis 不可用时返回空列表 */
    public List<Session> listSessions() {
        List<Session> sessions = new ArrayList<>();
        for (String key : redisGuard.scanKeys(ONLINE_PREFIX)) {
            String json = redisGuard.get(key);
            if (!StringUtils.hasText(json)) {
                continue;
            }
            try {
                sessions.add(objectMapper.readValue(json, Session.class));
            } catch (RuntimeException e) {
                // 单条脏数据不能拖垮整张表：跳过并清理
                log.warn("在线会话数据无法解析，已忽略 key={}", key);
                redisGuard.remove(key);
            }
        }
        sessions.sort((a, b) -> Long.compare(b.loginAtMillis(), a.loginAtMillis()));
        return sessions;
    }

    /**
     * 标记某用户「此刻之前的令牌全部失效」。
     *
     * @param ttlSeconds 令牌有效期秒数（通常传 jwt.expiration/1000）；&lt;= 0 时用兜底时长
     * @return 是否写入成功（Redis 不可用时 false，调用方必须据此报错而不是假报成功）
     */
    public boolean kick(Long userId, long ttlSeconds) {
        long now = System.currentTimeMillis();
        // 标记只需要活到「被踢之前签发的最后一枚令牌自然过期」，所以 TTL 取令牌有效期即可
        Duration ttl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : KICK_RETENTION;
        redisGuard.write(KICKED_PREFIX + userId, String.valueOf(now), ttl);
        unregister(userId);
        // write 失败会降级且不抛异常，这里用「能否读回」确认真的写进去了
        return StringUtils.hasText(redisGuard.get(KICKED_PREFIX + userId));
    }

    /**
     * 该令牌是否已被强制下线。
     *
     * <p>比较的是<b>令牌签发时刻</b>与<b>踢出时刻</b>：只有「踢出之前签发」的令牌才算失效，
     * 用户被踢后重新登录拿到的新令牌签发时间更晚，不受影响。</p>
     */
    public boolean isKicked(Long userId, long issuedAtMillis) {
        if (userId == null) {
            return false;
        }
        String kickedAt = redisGuard.get(KICKED_PREFIX + userId);
        if (!StringUtils.hasText(kickedAt)) {
            return false;
        }
        try {
            return issuedAtMillis < Long.parseLong(kickedAt);
        } catch (NumberFormatException e) {
            log.warn("踢出标记格式异常 userId={} value={}", userId, kickedAt);
            return false;
        }
    }

    /** UA 只用于展示，超长会把列表撑乱，截断到 120 字符 */
    private String truncate(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        return userAgent.length() <= 120 ? userAgent : userAgent.substring(0, 120) + "…";
    }
}
