package com.kellen.oa_system.hr.performance.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.performance.dto.PerfAppealHandleRequest;
import com.kellen.oa_system.hr.performance.dto.PerfAppealRequest;
import com.kellen.oa_system.hr.performance.dto.PerfCalibrateRequest;
import com.kellen.oa_system.hr.performance.dto.PerfScoreRequest;
import com.kellen.oa_system.hr.performance.dto.PerfSheetPageQuery;
import com.kellen.oa_system.hr.performance.dto.PerfTargetSaveRequest;
import com.kellen.oa_system.hr.performance.vo.PerfAppealVO;
import com.kellen.oa_system.hr.performance.vo.PerfDistributionVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetDetailVO;
import com.kellen.oa_system.hr.performance.vo.PerfSheetVO;
import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 绩效评分链路：目标 → 自评 → 上级评分 → 校准 → 确认 → 申诉 → 结果视图。
 *
 * <p>每一步都要留痕，且都受「当前阶段」约束——跳过阶段直接打分会让结果无法解释。</p>
 */
public interface PerfScoreService {

    /** 当前登录人在指定周期的考核表（没有则抛 6516） */
    PerfSheetDetailVO mySheet(Long cycleId);

    /** 我的绩效历史（只含已归档/已出结果的周期） */
    PageResult<PerfSheetVO> myHistory(Integer pageNo, Integer pageSize);

    /** 提交目标（仅目标设定阶段、仅本人、仅未锁定） */
    void saveTarget(PerfTargetSaveRequest request);

    /** 上级审核目标：pass=true 锁定，false 驳回重填 */
    void approveTarget(Long sheetId, Boolean pass, String opinion);

    /** 提交自评 */
    void submitSelf(PerfScoreRequest request);

    /** 上级评分（直属上级，或 HR 代评） */
    void submitManager(PerfScoreRequest request);

    /** HR 校准（改分/改等级必须填理由） */
    void calibrate(PerfCalibrateRequest request);

    /** 员工确认知悉 */
    void confirm(Long sheetId);

    /** 提交申诉（每张表一次，确认后 5 个工作日内） */
    void appeal(PerfAppealRequest request);

    /** 处理申诉 */
    void handleAppeal(PerfAppealHandleRequest request);

    /** 待我处理的申诉列表 */
    List<PerfAppealVO> pendingAppeals(Long cycleId);

    PageResult<PerfSheetVO> pageSheets(PerfSheetPageQuery query);

    /** 团队绩效：只看直属下属 */
    PageResult<PerfSheetVO> pageTeam(PerfSheetPageQuery query);

    PerfSheetDetailVO detail(Long sheetId);

    /** 分布统计（按部门） */
    List<PerfDistributionVO> distribution(Long cycleId);

    void export(Long cycleId, HttpServletResponse response);

    /**
     * 取某人在某月的绩效系数（供薪酬引用）。
     *
     * @return 归档周期的系数；没有归档结果时返回 null，调用方按 1.0 处理并标注
     */
    BigDecimal coefficientOf(Long userId, String month);

    /**
     * 批量取绩效系数（BE-OPT-04）。
     *
     * <p>核算时按人循环调 {@code coefficientOf} 会让「查已归档周期」这一步对每个员工重复一次，
     * 而周期列表与 userId 无关。批量版本只查一次周期、一次考核表。
     * 无归档结果的 userId <b>不会</b>出现在返回 Map 中（调用方按「无结果 = 系数 1.0」处理）。</p>
     */
    java.util.Map<Long, BigDecimal> coefficientMap(java.util.Collection<Long> userIds, String month);
}
