package com.kellen.oa_system.hr.employee.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 建档完整度清单里的一行（某个维度缺失的人）。
 *
 * <p>字段刻意只放「HR 用来定位这个人、并判断该找谁补」的信息：
 * 工号、姓名、部门、任职状态、入职日期。不放手机号之类的联系方式——
 * 清单是待办列表，不是通讯录；真要联系，点进档案页更合适。</p>
 */
@Data
@Builder
public class CompletenessItemVO {

    private Long userId;

    private String workNumber;

    private String realName;

    private String deptName;

    /** 任职状态编码：1待入职 2试用 3正式 4待离职 */
    private Integer employmentStatus;

    private String employmentStatusLabel;

    private LocalDate entryDate;

    /** 直属上级姓名（「无直属上级」这一维度下必然为空，其余维度用来提示找谁补） */
    private String directLeaderName;
}
