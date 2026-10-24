package com.kellen.oa_system.hr.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 招聘职位。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_recruit_post")
public class RecruitPost extends AuditEntity {

    /** 关联需求 */
    private Long demandId;

    /** 职位名称 */
    private String postName;

    /** 工作地点 */
    private String city;

    /** 薪资范围下限 */
    private BigDecimal salaryMin;

    /** 薪资范围上限 */
    private BigDecimal salaryMax;

    /** 招聘人数 */
    private Integer headCount;

    /** 1内推 2官网 3招聘平台 */
    private Integer channel;

    /** 1草稿 2已发布 3已下线 */
    private Integer status;

    /** 负责HR */
    private Long hrId;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 备注 */
    private String remark;
}
