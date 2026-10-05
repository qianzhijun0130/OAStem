package com.kellen.oa_system.profile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更换头像入参。
 *
 * <p>传入的是 {@code /file/upload} 返回的相对地址，服务端会校验前缀，
 * 避免把任意外部 URL 写成头像（既可能是失效图，也可能是被拿来当图床或引流）。
 */
@Data
public class ProfileAvatarRequest {

    @NotBlank(message = "头像地址不能为空")
    private String avatar;
}
