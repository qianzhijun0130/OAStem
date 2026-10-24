package com.kellen.oa_system.hr.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 招聘面试评价（每个面试官一行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_recruit_feedback")
public class RecruitFeedback extends AuditEntity {

    /** 面试 */
    private Long interviewId;

    /** 面试官 */
    private Long interviewerId;

    /** 专业能力 1~5 */
    private Integer professional;

    /** 沟通表达 1~5 */
    private Integer communication;

    /** 逻辑思维 1~5 */
    private Integer logicScore;

    /** 文化匹配 1~5 */
    private Integer cultureMatch;

    /** 1通过 2待定 3不通过 */
    private Integer conclusion;

    /** 评语 */
    private String comment;
}
