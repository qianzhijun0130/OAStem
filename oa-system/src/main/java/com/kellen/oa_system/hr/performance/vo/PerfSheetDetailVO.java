package com.kellen.oa_system.hr.performance.vo;

import com.kellen.oa_system.hr.performance.vo.PerfSheetItemVO;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 考核表详情（含逐项与申诉）。
 */
@Data
public class PerfSheetDetailVO {

    /** 主键 */
    private Long id;

    /** 周期ID */
    private Long cycleId;

    /** 周期编码 */
    private String cycleCode;

    /** 周期名称 */
    private String cycleName;

    /** 周期阶段 */
    private Integer stage;

    /** 阶段文本 */
    private String stageLabel;

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


    /** 自评总分 */
    private BigDecimal selfTotal;

    /** 上级评分总分 */
    private BigDecimal managerTotal;

    /** 最终总分 */
    private BigDecimal totalScore;

    /** 等级 */
    private String gradeCode;

    /** 等级名称 */
    private String gradeName;

    /** 系数 */
    private BigDecimal coefficient;

    /** 上级总评语 */
    private String managerComment;

    /** 超期未自评 */
    private Integer selfMissing;

    /** HR 代评 */
    private Integer hrScored;

    /** 备注 */
    private String remark;

    /** 指标行 */
    private List<PerfSheetItemVO> items;

    /** 申诉（可能为空） */
    private PerfAppealVO appeal;

}
