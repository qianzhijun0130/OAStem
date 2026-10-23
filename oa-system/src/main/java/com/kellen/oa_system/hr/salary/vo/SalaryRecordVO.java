package com.kellen.oa_system.hr.salary.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 月度核算列表视图。
 */
@Data
public class SalaryRecordVO {

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

    /** 月份 */
    private String month;

    /** 应发 */
    private BigDecimal grossPay;

    /** 考勤扣款 */
    private BigDecimal attendanceDeduct;

    /** 社保个人 */
    private BigDecimal socialPersonal;

    /** 公积金个人 */
    private BigDecimal fundPersonal;

    /** 个税 */
    private BigDecimal tax;

    /** 其他扣款 */
    private BigDecimal otherDeduct;

    /** 实发 */
    private BigDecimal netPay;

    /** 单位成本（社保+公积金单位部分） */
    private BigDecimal companyCost;

    /** 状态 */
    private Integer status;

    /** 状态文本 */
    private String statusLabel;

    /** 核算时间 */
    private LocalDateTime calcTime;

    /** 发放时间 */
    private LocalDateTime payTime;

}
