package com.kellen.oa_system.hr.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 职位保存请求。
 */
@Data
public class RecruitPostSaveRequest {

    /** 主键（更新时传） */
    private Long id;

    /** 关联需求 */
    private Long demandId;

    /** 职位名称 */
    @NotBlank(message = "职位名称不能为空")
    private String postName;

    /** 工作地点 */
    private String city;

    /** 薪资范围下限 */
    private BigDecimal salaryMin;

    /** 薪资范围上限 */
    private BigDecimal salaryMax;

    /** 招聘人数 */
    @NotNull(message = "招聘人数不能为空")
    @Positive(message = "招聘人数必须大于 0")
    private Integer headCount;

    /** 1内推 2官网 3招聘平台 */
    private Integer channel;

    /** 1草稿 2已发布 3已下线 */
    private Integer status;

    /** 负责HR */
    private Long hrId;

    /** 备注 */
    private String remark;
}
