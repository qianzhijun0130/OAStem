package com.kellen.oa_system.hr.salary.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 社保政策视图。
 */
@Data
public class SocialPolicyVO {

    /** 主键 */
    private Long id;

    /** 城市 */
    private String city;

    /** 年度 */
    private Integer policyYear;

    /** 社保基数下限 */
    private BigDecimal baseMin;

    /** 社保基数上限 */
    private BigDecimal baseMax;

    /** 公积金基数下限 */
    private BigDecimal fundBaseMin;

    /** 公积金基数上限 */
    private BigDecimal fundBaseMax;

    /** 养老个人 */
    private BigDecimal pensionPersonal;

    /** 养老单位 */
    private BigDecimal pensionCompany;

    /** 医疗个人 */
    private BigDecimal medicalPersonal;

    /** 医疗单位 */
    private BigDecimal medicalCompany;

    /** 失业个人 */
    private BigDecimal unemploymentPersonal;

    /** 失业单位 */
    private BigDecimal unemploymentCompany;

    /** 工伤单位 */
    private BigDecimal injuryCompany;

    /** 生育单位 */
    private BigDecimal maternityCompany;

    /** 公积金个人 */
    private BigDecimal fundPersonal;

    /** 公积金单位 */
    private BigDecimal fundCompany;

    /** 个人合计比例 */
    private BigDecimal personalRateTotal;

    /** 备注 */
    private String remark;

}
