package com.kellen.oa_system.hr.recruit.vo;

import lombok.Data;

import java.util.List;

/**
 * 招聘看板 VO。
 */
@Data
public class RecruitBoardVO {

    /** 在招职位数 */
    private Integer postingCount;

    /** 需求总人数 */
    private Integer demandHeadCount;

    /** 已招到岗人数 */
    private Integer hiredCount;

    /** 平均招聘周期（天） */
    private Integer avgCycleDays;

    /** 各阶段人数（漏斗） */
    private List<StageCount> stageFunnel;

    /** 各渠道转化 */
    private List<ChannelStat> channelStats;

    /** 淘汰原因分布 */
    private List<RejectStat> rejectStats;

    /** 部门维度 */
    private List<DeptStat> deptStats;

    /** 阶段漏斗项 */
    @Data
    public static class StageCount {
        private Integer stage;
        private String label;
        private Integer count;
        /** 相对上一阶段的转化率（百分比） */
        private Integer convertRate;
    }

    /** 渠道统计 */
    @Data
    public static class ChannelStat {
        private Integer source;
        private String label;
        /** 简历数 */
        private Integer resumeCount;
        /** 面试数 */
        private Integer interviewCount;
        /** 入职数 */
        private Integer hireCount;
        /** 面试转化率（面试数/简历数，百分比） */
        private Integer interviewRate;
        /** 入职转化率（入职数/简历数，百分比） */
        private Integer hireRate;
    }

    /** 淘汰原因统计 */
    @Data
    public static class RejectStat {
        private Integer rejectType;
        private String label;
        private Integer count;
    }

    /** 部门统计 */
    @Data
    public static class DeptStat {
        private Long deptId;
        private String deptName;
        /** 需求数 */
        private Integer demandCount;
        /** 需求人数 */
        private Integer headCount;
        /** 到岗人数 */
        private Integer hiredCount;
    }
}
