package com.kellen.oa_system.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工（用户）新增 / 修改入参。
 *
 * <p>id 为空表示新增，非空表示修改。密码字段仅在填写时生效：
 * 新增时留空则使用系统统一的初始密码；修改时留空表示不修改密码。</p>
 */
@Data
public class UserSaveRequest {

    /** 用户ID；新增时为空 */
    private Long id;

    @NotBlank(message = "登录账号不能为空")
    @Size(max = 30, message = "登录账号长度不能超过 30")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,30}$", message = "登录账号只能包含字母、数字、下划线，长度 3-30")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String realName;

    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    /** 性别：1男 2女；不传时默认为男 */
    @Min(value = 1, message = "性别只能是 1男 2女")
    @Max(value = 2, message = "性别只能是 1男 2女")
    private Integer gender;

    @NotBlank(message = "手机号不能为空")
    @Size(max = 20, message = "手机号长度不能超过 20")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确，请填写 11 位手机号")
    private String mobile;

    // 使用严格正则而非 @Email：Jakarta 默认的 @Email 不要求域名含点，a@b 会被放行
    @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;

    @NotNull(message = "请选择所属部门")
    private Long deptId;

    @Size(max = 50, message = "岗位长度不能超过 50")
    private String position;

    /** 工号；留空由系统按部门自动分配（部门前缀 + 部门内流水号） */
    @Size(max = 30, message = "工号长度不能超过 30")
    private String workNumber;

    /** 入职日期；新增时留空自动取表单提交当日 */
    private LocalDate entryDate;

    @NotNull(message = "请至少分配一个角色")
    @Size(min = 1, message = "请至少分配一个角色")
    private List<Long> roleIds;

    @Size(min = 6, max = 20, message = "密码长度需为 6-20 位")
    private String password;

    /** 状态：0禁用 1启用 2锁定 */
    @Min(value = 0, message = "状态只能是 0禁用 1启用 2锁定")
    @Max(value = 2, message = "状态只能是 0禁用 1启用 2锁定")
    private Integer status;
}
