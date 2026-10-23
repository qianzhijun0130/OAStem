package com.kellen.oa_system.hr.employee.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 花名册行视图。
 *
 * <p>不含身份证、银行卡等敏感字段——脱敏值也不放，避免「一眼看到尾号」被用于社工。
 * 需要查看敏感信息请进档案详情（有权限校验与操作日志）。</p>
 */
@Data
public class EmployeeVO {

    private Long id;

    private String username;

    private String realName;

    private Integer gender;

    /** 脱敏手机号，如 138****8000 */
    private String mobile;

    private String email;

    private Long companyId;

    private String companyName;

    private Long deptId;

    private String deptName;

    private Long positionId;

    private String positionName;

    /** 冗余的岗位显示名（历史数据可能只有文本没有 positionId） */
    private String position;

    private String workNumber;

    private LocalDate entryDate;

    /** 司龄（年，保留 1 位小数），由后端计算，避免前后端各算一套 */
    private String seniority;

    /** 任职状态：1待入职 2试用 3正式 4待离职 5已离职 6退休 */
    private Integer employmentStatus;

    private String employmentStatusLabel;

    /** 直属上级姓名 */
    private String directLeaderName;

    /** 账号状态：0禁用 1启用 2锁定（与任职状态是两个维度） */
    private Integer status;

    private LocalDateTime createTime;
}
