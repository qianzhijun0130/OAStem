package com.kellen.oa_system.hr.position.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位视图对象。
 */
@Data
public class PositionVO {

    private Long id;

    private String positionCode;

    private String positionName;

    private Long companyId;

    private Long deptId;

    /** 部门名称（一次批量查询后回填，避免逐行关联） */
    private String deptName;

    /** 序列：M管理 P技术 S职能 O操作 */
    private String series;

    private String levelCode;

    /** 编制数，0=未设置 */
    private Integer headcount;

    /**
     * 在岗人数（任职状态 试用/正式/待离职）。
     *
     * <p>口径与花名册一致：{@code employment_status IN (2,3,4)}，
     * 不含待入职与已离职，也不受账号状态影响。</p>
     */
    private Integer onDutyCount;

    /** 是否超编（在岗人数 > 编制数且编制数 > 0），仅提示不拦截 */
    private Boolean overHeadcount;

    private String description;

    private Integer status;

    private Integer sort;

    private LocalDateTime createTime;
}
