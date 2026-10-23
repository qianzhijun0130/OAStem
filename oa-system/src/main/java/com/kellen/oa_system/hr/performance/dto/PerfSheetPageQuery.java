package com.kellen.oa_system.hr.performance.dto;


import lombok.Data;

/**
 * 考核表分页查询。
 */
@Data
public class PerfSheetPageQuery {

    /** 周期ID */
    private Long cycleId;

    /** 部门 */
    private Long deptId;

    /** 进度筛选：target/self/manager/confirm/all */
    private String stage;

    /** 姓名/工号 */
    private String keyword;

    /** 页码 */
    private Integer pageNo;

    /** 每页条数 */
    private Integer pageSize;

}
