package com.kellen.oa_system.hr.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 社保公积金政策（按城市 + 年度）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_social_policy")
public class SocialPolicy extends AuditEntity {

    /** 参保城市 */
    private String city;

    /** 政策年度 */
    private Integer policyYear;

    /** 社保基数下限 */
    private BigDecimal baseMin;

    /** 社保基数上限（0=不封顶） */
    private BigDecimal baseMax;

    /** 公积金基数下限 */
    private BigDecimal fundBaseMin;

    /** 公积金基数上限（0=不封顶） */
    private BigDecimal fundBaseMax;

    /** 养老个人比例 */
    private BigDecimal pensionPersonal;

    /** 养老单位比例 */
    private BigDecimal pensionCompany;

    /** 医疗个人比例 */
    private BigDecimal medicalPersonal;

    /** 医疗单位比例 */
    private BigDecimal medicalCompany;

    /** 失业个人比例 */
    private BigDecimal unemploymentPersonal;

    /** 失业单位比例 */
    private BigDecimal unemploymentCompany;

    /** 工伤单位比例 */
    private BigDecimal injuryCompany;

    /** 生育单位比例 */
    private BigDecimal maternityCompany;

    /** 公积金个人比例 */
    private BigDecimal fundPersonal;

    /** 公积金单位比例 */
    private BigDecimal fundCompany;

    /** 备注 */
    private String remark;
}
