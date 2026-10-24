package com.kellen.oa_system.hr.recruit.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试 VO（含各面试官评价）。
 */
@Data
public class RecruitInterviewVO {

    private Long id;
    private Long candidateId;
    private String candidateName;
    private String candidateMobile;
    private Long postId;
    private String postName;
    private Integer roundNo;
    private LocalDateTime interviewTime;
    private Integer way;
    private String wayLabel;
    private String location;
    private String interviewerIds;
    private List<String> interviewerNames;
    private Integer status;
    private String statusLabel;
    private Integer conclusion;
    private String conclusionLabel;
    private String summary;
    private String continueReason;

    /** 已提交评价的面试官数 */
    private Integer feedbackCount;

    /** 应提交评价的面试官数 */
    private Integer interviewerCount;

    /** 全部提交后才能汇总 */
    private Boolean allSubmitted;

    /** 各面试官评价 */
    private List<RecruitFeedbackVO> feedbacks;

    private String remark;
    private LocalDateTime createTime;
}
