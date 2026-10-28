package com.kellen.oa_system.hr.performance.support;

import java.util.Map;

/**
 * 绩效评分链路共享常量（BE-OPT-08 拆分时从 {@code PerfScoreServiceImpl} 抽出）。
 *
 * <p>阶段码是这条链路的骨架：四个写动作各卡一个阶段门槛，改这里的值等于改流程，
 * 因此集中一处而不是散在四个协作类里。</p>
 */
public final class PerfConstants {

    private PerfConstants() {
    }

    public static final Map<Integer, String> STAGE_LABEL = Map.of(
            1, "目标设定", 2, "自评中", 3, "上级评分", 4, "校准", 5, "结果确认", 6, "已归档");

    public static final Map<Integer, String> TARGET_STATUS_LABEL = Map.of(
            0, "未提交", 1, "待审核", 2, "已锁定", 3, "已驳回");

    public static final Map<Integer, String> APPEAL_STATUS_LABEL = Map.of(
            1, "待处理", 2, "维持原结果", 3, "调整");

    public static final int STAGE_TARGET = 1;
    public static final int STAGE_SELF = 2;
    public static final int STAGE_MANAGER = 3;
    public static final int STAGE_CALIBRATE = 4;
    public static final int STAGE_CONFIRM = 5;
    public static final int STAGE_ARCHIVED = 6;

    /** 申诉窗口：结果确认后 5 个工作日 */
    public static final int APPEAL_WORK_DAYS = 5;
}
