package com.kellen.oa_system.hr.training.support;

import java.util.Map;

/**
 * 培训模块共享常量（BE-OPT-08 拆分时从 {@code TrainServiceImpl} 抽出）。
 *
 * <p>这些字典映射与状态码原本是 ServiceImpl 的 {@code private static}，被课程、期次、报名、
 * 统计四个协作类同时用到；拆开后必须放在一个中立位置，否则要么重复定义（改一处漏一处），
 * 要么让协作类互相依赖。</p>
 */
public final class TrainConstants {

    private TrainConstants() {
    }

    /** 课程类型 */
    public static final Map<Integer, String> TRAIN_TYPE_LABEL = Map.of(
            1, "新员工入职", 2, "岗位技能", 3, "管理能力", 4, "合规安全", 5, "通用素养");

    /** 期次状态 */
    public static final Map<Integer, String> SESSION_STATUS_LABEL = Map.of(
            1, "报名中", 2, "进行中", 3, "已结束", 4, "已取消");

    /** 报名状态 */
    public static final Map<Integer, String> ENROLL_STATUS_LABEL = Map.of(
            1, "已报名", 2, "候补", 3, "已取消", 4, "已签到", 5, "缺勤", 6, "已完成", 7, "爽约");

    /** 报名方式 */
    public static final Map<Integer, String> ENROLL_WAY_LABEL = Map.of(
            1, "自助报名", 2, "HR指派");

    // ---------- 报名状态码 ----------
    public static final int ENROLL_ENROLLED = 1;
    public static final int ENROLL_WAITING = 2;
    public static final int ENROLL_CANCELLED = 3;
    public static final int ENROLL_CHECKED = 4;
    public static final int ENROLL_ABSENT = 5;
    public static final int ENROLL_COMPLETED = 6;
    public static final int ENROLL_NOSHOW = 7;

    // ---------- 期次状态码 ----------
    public static final int SESSION_OPENING = 1;
    public static final int SESSION_CANCELED = 4;

    /** 取消报名时限：开课前 24 小时 */
    public static final int CANCEL_LIMIT_HOURS = 24;

    /** 爽约次数上限：累计 3 次限制报名 1 个月 */
    public static final int ABSENCE_LIMIT = 3;

    /** 限制报名天数 */
    public static final int RESTRICT_DAYS = 30;
}
