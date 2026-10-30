package com.kellen.oa_system.hr.recruit.support;

import java.util.Map;

/**
 * 招聘模块的字典与状态码。
 *
 * <p>这里共 13 张字典表，看起来啰嗦，但每一张都同时被「列表展示」和「业务判定」使用。
 * 例如 {@link #STAGE_HIRED}：展示上是「已入职」，判定上是「不可回退」；
 * 若两处各写字面量 7，某天改了阶段编号就会出现「页面显示已完成、系统仍允许回退」。</p>
 */
public final class RecruitConstants {

    private RecruitConstants() {
    }

    // ---------- 字典 ----------

    public static final Map<Integer, String> DEMAND_STATUS_LABEL = Map.of(
            1, "待审批", 2, "招聘中", 3, "已暂停", 4, "已完成", 5, "已关闭", 6, "已驳回");

    public static final Map<Integer, String> URGENCY_LABEL = Map.of(1, "普通", 2, "紧急");

    public static final Map<Integer, String> REASON_TYPE_LABEL = Map.of(
            1, "新增", 2, "替补", 3, "储备");

    public static final Map<Integer, String> STAGE_LABEL = Map.of(
            1, "待筛选", 2, "初试", 3, "复试", 4, "终试", 5, "待定",
            6, "已发offer", 7, "已入职", 8, "已淘汰", 9, "已放弃");

    public static final Map<Integer, String> SOURCE_LABEL = Map.of(
            1, "内推", 2, "招聘平台", 3, "猎头", 4, "校招", 5, "主动投递");

    public static final Map<Integer, String> REJECT_LABEL = Map.of(
            1, "技能不匹配", 2, "经验不足", 3, "学历不符", 4, "薪资不匹配",
            5, "文化不匹配", 6, "候选人放弃");

    public static final Map<Integer, String> CHANNEL_LABEL = Map.of(
            1, "内推", 2, "官网", 3, "招聘平台");

    public static final Map<Integer, String> POST_STATUS_LABEL = Map.of(
            1, "草稿", 2, "已发布", 3, "已下线");

    public static final Map<Integer, String> INTERVIEW_STATUS_LABEL = Map.of(
            1, "待面试", 2, "已汇总", 3, "已取消");

    public static final Map<Integer, String> CONCLUSION_LABEL = Map.of(
            1, "通过", 2, "待定", 3, "不通过");

    public static final Map<Integer, String> WAY_LABEL = Map.of(
            1, "现场", 2, "电话", 3, "视频");

    public static final Map<Integer, String> OFFER_STATUS_LABEL = Map.of(
            1, "待审批", 2, "已发出", 3, "已接受", 4, "已拒绝", 5, "已过期", 6, "已作废");

    public static final Map<Integer, String> SEND_WAY_LABEL = Map.of(
            1, "邮件", 2, "短信", 3, "纸质");

    // ---------- 需求状态 ----------

    public static final int DEMAND_PENDING = 1;
    public static final int DEMAND_RECRUITING = 2;
    /** 已完成：看板把它与「招聘中」一起算进在招需求 */
    public static final int DEMAND_FINISHED = 4;
    public static final int DEMAND_CLOSED = 5;

    // ---------- 候选人阶段 ----------

    public static final int STAGE_SCREENING = 1;
    /** 终试：offer 之前的最低阶段（发 offer 要求 stage >= 4） */
    public static final int STAGE_FINAL = 4;
    public static final int STAGE_OFFER = 6;
    public static final int STAGE_HIRED = 7;
    public static final int STAGE_REJECTED = 8;
    public static final int STAGE_ABANDONED = 9;

    // ---------- offer 状态 ----------

    public static final int OFFER_PENDING = 1;
    public static final int OFFER_SENT = 2;
    public static final int OFFER_ACCEPTED = 3;
    public static final int OFFER_REFUSED = 4;
    public static final int OFFER_EXPIRED = 5;

    /** offer 默认有效期：自然日（约 5 个工作日） */
    public static final int OFFER_VALID_DAYS = 7;
}
