package com.kellen.oa_system.profile.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料修改入参。
 *
 * <p>只开放个人可自主维护的字段。真实姓名、工号、部门、岗位、入职时间属于人事档案，
 * 由管理员在「员工管理」中修改——若允许自助修改，考勤与审批的身份依据就失去了可信度。</p>
 */
@Data
public class ProfileUpdateRequest {

    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    /** 性别：0未知 1男 2女 */
    @Min(value = 1, message = "性别只能是 1男 2女")
    @Max(value = 2, message = "性别只能是 1男 2女")
    private Integer gender;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;
}
