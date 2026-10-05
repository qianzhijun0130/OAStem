package com.kellen.oa_system.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数。
 *
 * <p>入参一律用 DTO 承接并做校验，禁止用实体直接接收请求。</p>
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 100, message = "密码长度不能超过 100")
    private String password;
}
