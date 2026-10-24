package com.kellen.oa_system.hr.recruit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 面试评价提交请求。
 */
@Data
public class RecruitFeedbackRequest {

    /** 面试 */
    @NotNull(message = "面试不能为空")
    private Long interviewId;

    /** 专业能力 1~5 */
    @Min(value = 1, message = "专业能力评分须在 1~5 之间")
    @Max(value = 5, message = "专业能力评分须在 1~5 之间")
    private Integer professional;

    /** 沟通表达 1~5 */
    @Min(value = 1, message = "沟通表达评分须在 1~5 之间")
    @Max(value = 5, message = "沟通表达评分须在 1~5 之间")
    private Integer communication;

    /** 逻辑思维 1~5 */
    @Min(value = 1, message = "逻辑思维评分须在 1~5 之间")
    @Max(value = 5, message = "逻辑思维评分须在 1~5 之间")
    private Integer logicScore;

    /** 文化匹配 1~5 */
    @Min(value = 1, message = "文化匹配评分须在 1~5 之间")
    @Max(value = 5, message = "文化匹配评分须在 1~5 之间")
    private Integer cultureMatch;

    /** 1通过 2待定 3不通过 */
    @NotNull(message = "必须给出面试结论")
    private Integer conclusion;

    /** 评语 */
    private String comment;
}
