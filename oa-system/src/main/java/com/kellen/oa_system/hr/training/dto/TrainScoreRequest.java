package com.kellen.oa_system.hr.training.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 考核成绩录入请求。
 */
@Data
public class TrainScoreRequest {

    /** 报名记录 */
    @NotNull(message = "报名记录不能为空")
    private Long enrollId;

    /** 成绩 */
    @NotNull(message = "成绩不能为空")
    @DecimalMin(value = "0", message = "成绩须在 0~100 之间")
    @DecimalMax(value = "100", message = "成绩须在 0~100 之间")
    private BigDecimal score;

    /** 1考试 2作业 3讲师评估 4免考 */
    private Integer assessWay;

    /** 满意度 1~5 */
    @Min(value = 1, message = "满意度须在 1~5 之间")
    @Max(value = 5, message = "满意度须在 1~5 之间")
    private Integer satisfied;

    /** 证书附件 */
    private Long certFileId;

    /** 备注 */
    private String remark;
}
