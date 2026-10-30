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

    /** 结果来源：系统评分（走完四道门槛） */
    public static final int SOURCE_SYSTEM = 1;
    /** 结果来源：结果导入（历史迁移），前端标「导入」 */
    public static final int SOURCE_IMPORT = 2;

    /** 指标类型：1 定量 / 2 定性 / 3 加分项 / 4 扣分项（3、4 的权重为 0，分数直接加减） */
    public static final int INDICATOR_BONUS = 3;
    public static final int INDICATOR_PENALTY = 4;
}
