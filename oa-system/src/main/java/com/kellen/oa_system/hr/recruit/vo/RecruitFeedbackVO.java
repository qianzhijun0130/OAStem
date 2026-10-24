package com.kellen.oa_system.hr.recruit.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试评价 VO。
 */
@Data
public class RecruitFeedbackVO {

    private Long id;
    private Long interviewId;
    private Long interviewerId;
    private String interviewerName;
    private Integer professional;
    private Integer communication;
    private Integer logicScore;
    private Integer cultureMatch;
    private Integer conclusion;
    private String conclusionLabel;
    private String comment;
    private LocalDateTime createTime;
}
