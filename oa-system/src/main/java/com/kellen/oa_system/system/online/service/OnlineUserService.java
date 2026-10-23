package com.kellen.oa_system.system.online.service;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.security.OnlineUserRegistry;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.online.vo.OnlineUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 在线用户管理。
 *
 * <p>会话数据存在 Redis，所以这里没有 Mapper 事务：列表是快照、踢出是写标记，
 * 两者都**不做分页下推**（Redis 侧无法按用户名分页），而是取出后在内存里过滤分页。
 * 在线人数受登录用户数约束（本项目为几十人量级），这样做代价可忽略。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserService {

    private final OnlineUserRegistry onlineUserRegistry;
    private final UserMapper userMapper;

    /** 时间展示格式：与全站列表页一致，精确到秒 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 令牌有效期（毫秒）：踢出标记的 TTL 与它对齐 */
    @Value("${jwt.expiration}")
    private long tokenExpirationMillis;

    public PageResult<OnlineUserVO> page(String keyword, int pageNo, int pageSize) {
        String expected = normalize(keyword);
        List<OnlineUserVO> all = onlineUserRegistry.listSessions().stream()
                .filter(session -> expected == null
                        || contains(session.username(), expected)
                        || contains(session.realName(), expected))
                .map(this::toVO)
                .toList();

        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int from = Math.min((safePageNo - 1) * safePageSize, all.size());
        int to = Math.min(from + safePageSize, all.size());
        // PageResult.of 需要 total + current + size，与数据库分页保持同一结构，前端无需区别对待
        return PageResult.of(all.subList(from, to), (long) all.size(), (long) safePageNo, (long) safePageSize);
    }

    /**
     * 强制下线某个用户。
     *
     * <p>异常分支是刻意区分的：</p>
     * <ul>
     *   <li>踢自己 → 拒绝（会把管理员一起踢出，属于误操作）；</li>
     *   <li>用户不存在 → 用户不存在；</li>
     *   <li>用户存在但已不在线 → 明确告之「已不在线」，让前端刷新列表而不是假装成功；</li>
     *   <li>Redis 不可用 → 报错。这种情况下写不进标记，谎报成功会让管理员以为已生效。</li>
     * </ul>
     */
    public void kick(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户 ID 不能为空");
        }
        Long currentUserId = SecurityContextUtil.getCurrentUserId();
        if (Objects.equals(userId, currentUserId)) {
            throw new BizException(ErrorCode.ONLINE_KICK_SELF_FORBIDDEN);
        }
        if (userMapper.selectById(userId) == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (!onlineUserRegistry.hasSession(userId)) {
            throw new BizException(ErrorCode.ONLINE_USER_NOT_FOUND);
        }
        boolean kicked = onlineUserRegistry.kick(userId, tokenExpirationMillis / 1000);
        if (!kicked) {
            throw new BizException(ErrorCode.ONLINE_KICK_UNAVAILABLE);
        }
        log.info("管理员强制下线用户 userId={} operatorId={}", userId, currentUserId);
    }

    private OnlineUserVO toVO(OnlineUserRegistry.Session session) {
        OnlineUserVO vo = new OnlineUserVO();
        vo.setUserId(session.userId());
        vo.setUsername(session.username());
        vo.setRealName(session.realName());
        vo.setClientIp(session.clientIp());
        vo.setUserAgent(session.userAgent());
        vo.setLoginTime(formatTime(session.loginAtMillis()));
        vo.setExpireTime(formatTime(session.expireAtMillis()));
        long minutes = Math.max(0, (System.currentTimeMillis() - session.loginAtMillis()) / 60_000);
        vo.setOnlineMinutes(minutes);
        return vo;
    }

    private String formatTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                .format(TIME_FORMATTER);
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
    }

    private boolean contains(String value, String expected) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(expected);
    }
}
