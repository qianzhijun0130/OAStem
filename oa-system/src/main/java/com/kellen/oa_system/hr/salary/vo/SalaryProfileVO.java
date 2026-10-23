package com.kellen.oa_system.hr.salary.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 薪资档案视图。
 */
@Data
public class SalaryProfileVO {

    /** 主键 */
    private Long id;

    /** 员工ID */
    private Long userId;

    /** 姓名 */
    private String realName;

    /** 工号 */
    private String workNumber;

    /** 部门ID */
    private Long deptId;

    /** 部门 */
    private String deptName;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 基本工资 */
    private BigDecimal baseSalary;

    /** 岗位工资 */
    private BigDecimal postSalary;

    /** 绩效工资基数 */
    private BigDecimal perfSalary;

    /** 固定津贴 */
    private BigDecimal allowance;

    /** 月标准工资（四项合计） */
    private BigDecimal standardSalary;

    /** 社保基数 */
    private BigDecimal socialBase;

    /** 公积金基数 */
    private BigDecimal fundBase;

    /** 参保城市 */
    private String city;

    /** 专项附加扣除 */
    private BigDecimal specialDeduct;

    /** 调薪原因 */
    private String changeReason;

    /** 创建时间 */
    private LocalDateTime createTime;

}
