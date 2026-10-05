package com.kellen.oa_system.system.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应。
 *
 * <p>只返回令牌与有效期，不回传密码等任何敏感字段。</p>
 */
@Data
@Builder
public class LoginResponse {

    private String token;

    /** 固定为 Bearer，前端拼接 Authorization 头时使用 */
    private String tokenType;

    /** 有效期（毫秒） */
    private Long expiresIn;

    private String username;

    private String realName;
}
