package com.kellen.oa_system.hr.salary.dto;


import lombok.Data;

/**
 * 薪资列表查询条件。
 */
@Data
public class SalaryPageQuery {

    /** 月份 yyyy-MM */
    private String month;

    /** 部门 */
    private Long deptId;

    /** 状态 */
    private Integer status;

    /** 工号/姓名关键词 */
    private String keyword;

    /** 页码 */
    private Integer pageNo;

    /** 每页条数 */
    private Integer pageSize;

}
