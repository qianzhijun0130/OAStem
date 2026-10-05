package com.kellen.oa_system.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志列表行。
 */
@Data
public class LoginLogVO {

    private Long id;

    private Long userId;

    private String username;

    /** 1账号密码登录 */
    private Integer loginType;

    private String ipAddress;

    private String location;

    private String browser;

    private String os;

    /** 1成功 0失败 */
    private Integer loginStatus;

    private String failReason;

    private LocalDateTime createTime;
}
