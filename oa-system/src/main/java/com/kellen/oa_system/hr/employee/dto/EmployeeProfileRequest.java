package com.kellen.oa_system.hr.employee.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工档案更新入参（分区提交，字段全部可选）。
 *
 * <p>档案页按 Tab 分区保存，因此这里不做「必填」约束——只校验格式。
 * 需要整体必填的字段（姓名、部门、工号）由用户管理模块维护，HR 档案页不重复实现。</p>
 */
@Data
public class EmployeeProfileRequest {

    /** 任职状态：1待入职 2试用 3正式 4待离职 5已离职 6退休；为空表示不变更 */
    @Min(value = 1, message = "任职状态取值 1~6")
    @Max(value = 6, message = "任职状态取值 1~6")
    private Integer employmentStatus;

    /** 任职状态变更说明，变更时必填（写入异动记录，便于回溯「为什么离职」） */
    @Size(max = 255, message = "变更说明长度不能超过 255")
    private String changeRemark;

    private Long positionId;

    /** 直属上级用户ID；不能指向自己，由服务端校验 */
    private Long directLeaderId;

    private LocalDate workStartDate;

    private LocalDate probationEndDate;

    @Pattern(regexp = "^$|^[0-9]{17}[0-9Xx]$", message = "身份证号格式不正确")
    private String idCard;

    private LocalDate birthDate;

    @Min(value = 1, message = "婚姻状况取值 1~4")
    @Max(value = 4, message = "婚姻状况取值 1~4")
    private Integer maritalStatus;

    @Size(max = 100, message = "户籍地长度不能超过 100")
    private String nativePlace;

    @Size(max = 255, message = "地址长度不能超过 255")
    private String address;

    @Size(max = 50, message = "紧急联系人长度不能超过 50")
    private String emergencyName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "紧急联系人手机号格式不正确")
    private String emergencyPhone;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;

    // ---------- 银行与社保（仅 HR 可写） ----------

    @Size(max = 100, message = "开户行长度不能超过 100")
    private String bankName;

    @Size(max = 64, message = "银行卡号长度不能超过 64")
    private String bankAccount;

    @Size(max = 64, message = "社保号长度不能超过 64")
    private String socialNo;

    @Size(max = 64, message = "公积金账号长度不能超过 64")
    private String fundNo;
}
