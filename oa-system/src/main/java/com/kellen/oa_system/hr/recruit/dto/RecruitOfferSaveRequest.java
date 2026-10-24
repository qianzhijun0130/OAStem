package com.kellen.oa_system.hr.recruit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Offer 发起请求。
 */
@Data
public class RecruitOfferSaveRequest {

    /** 候选人 */
    @NotNull(message = "候选人不能为空")
    private Long candidateId;

    /** 职位 */
    private Long postId;

    /** 岗位 */
    private Long positionId;

    /** 职级/岗位名称 */
    private String positionName;

    /** 薪资 */
    @NotNull(message = "薪资不能为空")
    private BigDecimal salary;

    /** 试用期月数 */
    private Integer probationMonths;

    /** 报到日期 */
    private LocalDate reportDate;

    /** 有效期截止（不传按 5 个工作日算） */
    private LocalDate expireDate;

    /** 1邮件 2短信 3纸质 */
    private Integer sendWay;

    /** 备注 */
    private String remark;
}
