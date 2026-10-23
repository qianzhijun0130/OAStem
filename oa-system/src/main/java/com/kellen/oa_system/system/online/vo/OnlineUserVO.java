package com.kellen.oa_system.system.online.vo;

import lombok.Data;

/**
 * 在线用户行（管理端）。
 *
 * <p>数据来源是 Redis 里的会话快照，不是数据库：用户表里没有「上次心跳」这类字段，
 * 也不应该为了一个运维视图去改动业务表。</p>
 */
@Data
public class OnlineUserVO {

    private Long userId;

    private String username;

    private String realName;

    private String clientIp;

    /** 浏览器 UA 摘要（已截断），用于区分终端 */
    private String userAgent;

    /**
     * 登录时间（yyyy-MM-dd HH:mm:ss）。
     *
     * <p>直接给字符串而不是 LocalDateTime：Jackson 3 默认把 LocalDateTime 序列化成
     * ISO（2026-10-23T09:06:46.165），前端表格里会多出 T 与毫秒，还得各自再格式化一次。
     * 运维页面只需要「能读」的时间，格式由后端定一处即可。</p>
     */
    private String loginTime;

    /** 令牌到期时间；到点后该行会自动从列表消失（Redis TTL） */
    private String expireTime;

    /** 已在线时长（分钟），由后端算好，避免前端各算一套 */
    private long onlineMinutes;
}
