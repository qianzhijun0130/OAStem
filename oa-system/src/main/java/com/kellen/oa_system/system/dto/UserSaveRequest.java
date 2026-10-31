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

    /**
     * 角色 id 列表。
     *
     * <p><b>新增</b>时留空（null / 空数组）由系统默认绑定「普通员工」（EMPLOYEE），
     * 之后由管理员在编辑员工时追加；<b>编辑</b>时留空表示「本次不改动角色」，
     * 不会被清空——任何账号至少要有一个角色才能正常使用。</p>
     */
    @Size(max = 20, message = "角色数量不能超过 20")
    private List<Long> roleIds;

    @Size(min = 6, max = 20, message = "密码长度需为 6-20 位")
    private String password;

    /** 状态：0禁用 1启用 2锁定 */
    @Min(value = 0, message = "状态只能是 0禁用 1启用 2锁定")
    @Max(value = 2, message = "状态只能是 0禁用 1启用 2锁定")
    private Integer status;

    // ------------------------------------------------------------------
    // 建档扩展字段（BIZ-OPT-09）
    //
    // 以下几个字段原先只能进档案页逐个补录，导致批量入职时「导完还要再点开每个人」。
    // 放到保存请求里后，导入与手工新增走同一条写入链路，不会出现「导入的没算试用期、
    // 手工加的算了」这种两套口径。
    //
    // 只在<b>新增</b>时生效；编辑档案仍走 EmployeeProfileRequest（那里有变更留痕与
    // 任职状态机校验，不能绕过）。
    // ------------------------------------------------------------------

    /** 身份证号（明文入参；落库由 TypeHandler 加密，同时自动算盲索引用于查重） */
    @Pattern(regexp = "^$|^[0-9]{17}[0-9Xx]$", message = "身份证号格式不正确")
    private String idCard;

    /** 出生日期；留空时按身份证号补算 */
    private LocalDate birthDate;

    /** 直属上级用户 ID */
    private Long directLeaderId;

    /** 首次参加工作时间（年假累计工龄用） */
    private LocalDate workStartDate;

    /** 试用期截止日；由「入职日 + 试用期月数 − 1 天」计算，不接受客户端直接指定 */
    private LocalDate probationEndDate;

    /** 任职状态：1待入职 2试用 3正式 4待离职 5已离职 6退休；留空时按入职日期判定 */
    private Integer employmentStatus;
}
