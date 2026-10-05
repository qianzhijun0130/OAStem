package com.kellen.oa_system.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码入参。
 */
@Data
public class PasswordChangeRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 长度规则与「新增员工」保持一致，避免两处口径不同 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度需为 6-20 位")
    private String newPassword;
}
