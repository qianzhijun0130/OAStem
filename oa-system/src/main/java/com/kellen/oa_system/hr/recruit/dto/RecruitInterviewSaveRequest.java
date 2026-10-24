package com.kellen.oa_system.hr.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试安排请求。
 */
@Data
public class RecruitInterviewSaveRequest {

    /** 主键（更新时传） */
    private Long id;

    /** 候选人 */
    @NotNull(message = "候选人不能为空")
    private Long candidateId;

    /** 职位 */
    private Long postId;

    /** 轮次 */
    private Integer roundNo;

    /** 面试时间 */
    @NotNull(message = "面试时间不能为空")
    private LocalDateTime interviewTime;

    /** 1现场 2电话 3视频 */
    private Integer way;

    /** 地点或线上链接 */
    private String location;

    /** 面试官ID列表 */
    @NotEmpty(message = "至少指定一位面试官")
    private List<Long> interviewerIds;

    /** 备注 */
    private String remark;
}
