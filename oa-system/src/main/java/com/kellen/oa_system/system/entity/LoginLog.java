package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 登录日志实体，对应表 oa_login_log。
 *
 * <p>该表只追加不修改、不做逻辑删除，因此不含 is_deleted / update_time。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_login_log")
public class LoginLog extends BaseEntity {

    private Long userId;

    private String username;

    /** 登录类型：1账号密码 2手机验证码 */
    private Integer loginType;

    private String ipAddress;

    private String location;

    private String browser;

    private String os;

    /** 状态：1成功 0失败 */
    private Integer loginStatus;

    private String failReason;

    private LocalDateTime createTime;
}
