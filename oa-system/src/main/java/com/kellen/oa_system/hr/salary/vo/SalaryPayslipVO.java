package com.kellen.oa_system.hr.salary.vo;

import com.kellen.oa_system.hr.salary.vo.SalaryItemLineVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 工资条 / 核算详情。
 */
@Data
public class SalaryPayslipVO {

    /** 主键 */
    private Long id;

    /** 员工ID */
    private Long userId;

    /** 姓名 */
    private String realName;

    /** 工号 */
    private String workNumber;

    /** 部门 */
    private String deptName;

    /** 月份 */
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

    /** 个人所得税 */
    private BigDecimal tax;

    /** 其他扣款 */
    private BigDecimal otherDeduct;

    /** 实发合计 */
    private BigDecimal netPay;

    /** 单位社保 */
    private BigDecimal socialCompany;

    /** 单位公积金 */
    private BigDecimal fundCompany;

    /** 社保基数 */
    private BigDecimal socialBase;

    /** 累计应纳税所得额 */
    private BigDecimal cumulativeTaxable;

    /** 专项附加扣除 */
    private BigDecimal specialDeduct;

    /** 状态 */
    private Integer status;

    /** 状态文本 */
    private String statusLabel;

    /** 备注 */
    private String remark;

    /** 核算时间 */
    private LocalDateTime calcTime;

    /** 发放时间 */
    private LocalDateTime payTime;

    /** 逐项明细 */
    private List<SalaryItemLineVO> items;

}
