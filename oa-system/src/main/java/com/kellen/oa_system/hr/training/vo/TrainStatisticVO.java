package com.kellen.oa_system.hr.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 培训统计 VO。
 */
@Data
public class TrainStatisticVO {

    /** 年度人均学时（总学时 ÷ 在职人数） */
    private BigDecimal avgHours;

    /** 总学时 */
    private BigDecimal totalHours;

    /** 在职人数 */
    private Integer staffCount;

    /** 必修完成率（已完成必修人数 ÷ 应完成人数，百分比） */
    private Integer requiredFinishRate;

    /** 应完成必修人次 */
    private Integer requiredShouldCount;

    /** 已完成必修人次 */
    private Integer requiredDoneCount;

    /** 课程满意度（平均分 1~5） */
    private BigDecimal satisfaction;

    /** 培训总投入 */
    private BigDecimal totalCost;

    /** 按课程统计 */
    private List<CourseStat> courseStats;

    /** 按部门统计 */
    private List<DeptStat> deptStats;

    /** 课程维度 */
    @Data
    public static class CourseStat {
        private Long courseId;
        private String courseName;
        private Integer required;
        /** 报名人次 */
        private Integer enrollCount;
        /** 结业人次 */
        private Integer completedCount;
        /** 出勤率（签到人数 ÷ 报名人数，百分比） */
        private Integer attendRate;
        /** 平均学时 */
        private BigDecimal avgHours;
        /** 平均满意度 */
        private BigDecimal satisfaction;
    }

    /** 部门维度 */
    @Data
    public static class DeptStat {
        private Long deptId;
        private String deptName;
        /** 参训人次 */
        private Integer enrollCount;
        /** 结业人次 */
        private Integer completedCount;
        /** 总学时 */
        private BigDecimal totalHours;
    }
}
