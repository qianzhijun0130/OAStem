package com.kellen.oa_system.hr.salary.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 社保公积金政策保存。
 */
@Data
public class SocialPolicySaveRequest {

    /** 主键（更新时传） */
    private Long id;

    /** 参保城市 */
    private String city;

    /** 政策年度 */
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

    /** 备注 */
    private String remark;

}
