package com.kellen.oa_system.hr.employee.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工档案详情。
 *
 * <p>侧表（合同 / 证照 / 异动）一次性带出，前端档案页按 Tab 直接渲染，
 * 不需要为每个 Tab 再发一次请求。</p>
 *
 * <p>敏感字段（身份证、银行卡）默认脱敏；{@link #canViewSensitive} 为 true 时返回原文。
 * 该开关由后端按角色计算，前端不得自行决定是否展示原文。</p>
 */
@Data
public class EmployeeDetailVO {

    private Long id;

    private String username;

    private String realName;

    private String nickname;

    private String avatar;

    private Integer gender;

    private String mobile;

    private String email;

    private Long companyId;

    private String companyName;

    private Long deptId;

    private String deptName;

    private Long positionId;

    private String positionName;

    private String position;

    private String workNumber;

    private LocalDate entryDate;

    /** 司龄，如「2.3 年」；入职日期为空时为 null */
    private String seniority;

    private Integer employmentStatus;

    private String employmentStatusLabel;

    private Long directLeaderId;

    private String directLeaderName;

    /** 账号状态：0禁用 1启用 2锁定 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    // ---------- 个人信息（身份证脱敏） ----------
    private String idCard;

    private LocalDate birthDate;

    private Integer maritalStatus;

    private String nativePlace;

    private String address;

    private String emergencyName;

    private String emergencyPhone;

    // ---------- 任职 ----------
    private LocalDate workStartDate;

    private LocalDate probationEndDate;

    // ---------- 银行与社保（卡号脱敏） ----------
    private String bankName;

    private String bankAccount;

    private String socialNo;

    private String fundNo;

    // ---------- 侧表 ----------
    private List<ContractVO> contracts;

    private List<CertVO> certs;

    private List<ChangeVO> changes;

    /** 是否可见敏感原文（HR / 超管为 true） */
    private Boolean canViewSensitive;
}
