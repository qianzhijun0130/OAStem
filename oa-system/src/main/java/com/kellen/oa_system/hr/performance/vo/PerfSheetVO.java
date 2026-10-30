package com.kellen.oa_system.hr.performance.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 考核表列表视图。
 */
@Data
public class PerfSheetVO {

    /** 主键 */
    private Long id;

    /** 周期ID */
    private Long cycleId;

    /** 周期编码 */
    private String cycleCode;

    /** 周期名称 */
    private String cycleName;

    /** 员工ID */
    private Long userId;

    /** 姓名 */
    private String realName;

    /** 工号 */
    private String workNumber;

    /** 部门 */
    private String deptName;

    /** 岗位 */
    private String positionName;

    /** 直属上级 */
    private String leaderName;

    /** 目标状态 */
    private Integer targetStatus;

    /** 目标状态文本 */
    private String targetStatusLabel;

    /** 自评状态 */
    private Integer selfStatus;

    /** 评分状态 */
    private Integer managerStatus;

    /** 校准状态 */
    private Integer calibrateStatus;

    /** 确认状态 */
    private Integer confirmStatus;
    /** 结果来源：1 系统评分 / 2 结果导入（历史迁移），前端据此标「导入」 */
    private Integer source;


    /** 最终总分 */
    private BigDecimal totalScore;

    /** 等级 */
    private String gradeCode;

    /** 系数 */
    private BigDecimal coefficient;

    /** 超期未自评 */
    private Integer selfMissing;

    /** HR 代评 */
    private Integer hrScored;

    /** 申诉状态（空=无申诉） */
    private Integer appealStatus;

    /** 备注 */
    private String remark;

}
