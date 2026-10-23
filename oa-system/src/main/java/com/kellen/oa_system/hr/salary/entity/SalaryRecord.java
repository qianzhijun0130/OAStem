package com.kellen.oa_system.hr.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度薪资核算主表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_salary_record")
public class SalaryRecord extends AuditEntity {

    /** 员工ID */
    private Long userId;

    /** 核算时的部门快照 */
    private Long deptId;

    /** 月份 yyyy-MM */
    private String month;

    /** 基本工资 */
    private BigDecimal baseSalary;

    /** 岗位工资 */
    private BigDecimal postSalary;

    /** 实发绩效 */
    private BigDecimal perfSalary;

    /** 固定津贴 */
    private BigDecimal allowance;

    /** 加班费 */
    private BigDecimal overtimePay;

    /** 其他收入 */
    private BigDecimal otherIncome;

    /** 应发合计 */
    private BigDecimal grossPay;

    /** 考勤扣款 */
    private BigDecimal attendanceDeduct;

    /** 社保个人 */
    private BigDecimal socialPersonal;

    /** 公积金个人 */
    private BigDecimal fundPersonal;

    /** 专项附加扣除 */
    private BigDecimal specialDeduct;

    /** 个人所得税 */
    private BigDecimal tax;

    /** 其他扣款 */
    private BigDecimal otherDeduct;

    /** 实发合计 */
    private BigDecimal netPay;

    /** 单位社保合计 */
    private BigDecimal socialCompany;

    /** 单位公积金 */
    private BigDecimal fundCompany;

    /** 使用的社保基数 */
    private BigDecimal socialBase;

    /** 本年累计应纳税所得额 */
    private BigDecimal cumulativeTaxable;

    /** 状态：1草稿 2已复核 3已封账 4已发放 */
    private Integer status;

    /** 核算时间 */
    private LocalDateTime calcTime;

    /** 复核时间 */
    private LocalDateTime reviewTime;

    /** 复核人 */
    private Long reviewBy;

    /** 发放时间 */
    private LocalDateTime payTime;

    /** 备注 */
    private String remark;
}
