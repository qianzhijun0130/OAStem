package com.kellen.oa_system.hr.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 招聘需求保存请求。
 */
@Data
public class RecruitDemandSaveRequest {

    /** 主键（更新时传） */
    private Long id;

    /** 需求部门 */
    @NotNull(message = "需求部门不能为空")
    private Long deptId;

    /** 需求岗位 */
    @NotNull(message = "需求岗位不能为空")
    private Long positionId;

    /** 招聘人数 */
    @NotNull(message = "招聘人数不能为空")
    @Positive(message = "招聘人数必须大于 0")
    private Integer headCount;

    /** 1普通 2紧急 */
    private Integer urgency;

    /** 期望到岗日期 */
    private LocalDate expectDate;

    /** 学历要求 */
    private String education;

    /** 经验年限要求 */
    private String experience;

    /** 技能要求 */
    private String skillRequire;

    /** 薪资范围下限 */
    private BigDecimal salaryMin;

    /** 薪资范围上限 */
    private BigDecimal salaryMax;

    /** 1新增 2替补 3储备 */
    private Integer reasonType;

    /** 招聘原因说明 */
    private String reason;

    /** 负责HR */
    private Long hrId;

    /** 备注 */
    private String remark;
}
