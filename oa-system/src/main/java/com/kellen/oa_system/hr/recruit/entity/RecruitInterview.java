package com.kellen.oa_system.hr.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 招聘面试安排。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_recruit_interview")
public class RecruitInterview extends AuditEntity {

    /** 候选人 */
    private Long candidateId;

    /** 职位 */
    private Long postId;

    /** 轮次 */
    private Integer roundNo;

    /** 面试时间 */
    private LocalDateTime interviewTime;

    /** 1现场 2电话 3视频 */
    private Integer way;

    /** 地点或线上链接 */
    private String location;

    /** 面试官ID，逗号分隔 */
    private String interviewerIds;

    /** 1待面试 2已汇总 3已取消 */
    private Integer status;

    /** 1通过 2待定 3不通过 */
    private Integer conclusion;

    /** 汇总评语 */
    private String summary;

    /** 有「不通过」仍继续推进的理由 */
    private String continueReason;

    /** 备注 */
    private String remark;
}
