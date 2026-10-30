package com.kellen.oa_system.hr.attendance.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 漏卡待处理项（数据来自 {@code oa_attendance.card_missing > 0} 的考勤行）。
 *
 * <p>与「主动补卡」的区别不是流程，而是<b>事实来源</b>：漏卡是系统已经知道这个人那天
 * 缺了哪一侧的卡（有排班，且有另一侧打卡或考勤机只剩一次刷卡），员工只是在认领一条
 * 既有事实；主动补卡则是什么记录都没有，靠自己声明。
 * 前者不该占用每月 3 次的额度，否则真实漏卡多的人反而补不了。</p>
 */
@Data
public class MissingCardVO {

    private Long attendanceId;

    private Long userId;

    private String workNumber;

    private String realName;

    private Long deptId;

    private String deptName;

    private LocalDate attendanceDate;

    /** 1 缺上班卡 / 2 缺下班卡 / 3 两侧都缺 */
    private Integer cardMissing;

    /** 缺卡描述，如「缺上班卡」「缺上下班卡」 */
    private String missingLabel;

    /** 缺卡方向明细：[1] / [2] / [1,2]，前端按方向渲染按钮 */
    private List<Integer> missingDirections = new ArrayList<>();

    /** 已有审批中或已通过补卡单的方向，这些方向按钮应置灰，避免重复提交 */
    private List<Integer> pendingDirections = new ArrayList<>();

    private Integer attendanceStatus;

    private String attendanceStatusLabel;
}
