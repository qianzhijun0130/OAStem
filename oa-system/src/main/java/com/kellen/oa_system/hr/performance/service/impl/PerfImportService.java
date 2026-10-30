package com.kellen.oa_system.hr.performance.service.impl;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.common.util.ExcelImportUtil;
import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfIndicator;
import com.kellen.oa_system.hr.performance.entity.PerfPlanGrade;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;
import com.kellen.oa_system.hr.performance.support.PerfConstants;
import com.kellen.oa_system.hr.performance.support.PerfImportLoader;
import com.kellen.oa_system.hr.performance.support.PerfImportTemplate;
import com.kellen.oa_system.hr.performance.support.PerfImportWriter;
import com.kellen.oa_system.hr.performance.support.PerfScoreCalculator;
import com.kellen.oa_system.hr.performance.vo.PerfImportResultVO;
import com.kellen.oa_system.system.entity.User;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 绩效结果导入（历史迁移）。
 *
 * <p>用途很具体：把老系统或纸质台账里的历史绩效搬进来，让「系数取已归档周期」这条链路
 * 在历史月份上也成立。它不是「HR 批量打分」的捷径——</p>
 *
 * <ul>
 *   <li><b>只允许已结束的周期</b>（已归档或截止日已过）。否则可以绕过自评/上级评/校准/确认
 *       直接把结果写进去，等同于把流程架空，且事后无法从数据上分辨谁走过流程。</li>
 *   <li><b>导入的成绩标 {@code source=2}</b>，与系统评分区分。员工看到一条 85 分要能判断
 *       它是不是自己上司打的，申诉时 HR 也要说得清来源。</li>
 *   <li><b>计算口径复用 {@link PerfScoreCalculator}</b>：否则导入算出的总分与归档、薪酬引用的
 *       数不一致，同一个人在系统里有两个分数。</li>
 * </ul>
 *
 * <p>逐行校验、坏行不阻断整批（与考勤机导入同一取舍）：一次几十行历史数据错一两行是常态，
 * 整批回滚只会让人对着「导入失败」猜是哪一行。所以<b>成败看 {@code imported/failed} 字段，
 * 不看接口是否报错</b>。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfImportService {

    /** 单批上限：历史迁移按周期导，一个周期通常几十到几百人 */
    public static final int MAX_ROWS = 5000;

    private static final int MAX_ERROR_ROWS = 50;

    /** 总分与明细交叉校验的容差：双方都保留 1 位小数，允许 0.1 的舍入差 */
    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.1");

    private final PerfImportLoader loader;
    private final PerfImportWriter writer;

    // ==================================================================
    // 模板下载
    // ==================================================================

    /**
     * 按周期方案生成导入模板：列 = 工号 + 姓名 + 各指标(权重%) + 总分 + 评语。
     *
     * <p>指标列带权重后缀不是装饰——导入时用它校验「文件与方案是否还对得上」，方案改过权重后
     * 拿旧模板导入会被直接拒绝，而不是按旧权重折算出一批错数。</p>
     */
    public void downloadTemplate(Long cycleId, HttpServletResponse response) {
        PerfCycle cycle = loader.requireCycle(cycleId);
        List<PerfImportTemplate.PlanLink> links = loader.loadLinks(cycle.getPlanId());
        if (links.isEmpty()) {
            throw new BizException(ErrorCode.PERF_IMPORT_NO_INDICATOR);
        }
        Map<Long, PerfIndicator> indicators = loader.loadIndicators(links);

        List<String> headers = new ArrayList<>(List.of("工号", "姓名"));
        for (PerfImportTemplate.PlanLink link : links) {
            PerfIndicator indicator = indicators.get(link.indicatorId());
            String name = indicator == null || indicator.getIndicatorName() == null
                    ? "未知指标" : indicator.getIndicatorName();
            headers.add(name + "(" + weightText(link.weight()) + "%)");
        }
        headers.add("总分");
        headers.add("评语");

        List<String> notes = List.of(
                "1. 工号必填，须与系统中员工的工号完全一致；姓名仅用于核对，可不填。",
                "2. 各指标列的表头形如「指标名(权重%)」，请勿改动；方案调整过权重时需重新下载模板。",
                "3. 指标得分范围 0~100；加分项/扣分项直接填分（如 3 表示加 3 分、2 表示扣 2 分）。",
                "4. 「总分」选填：填了会与明细交叉校验（差超过 0.1 该行失败），不填由系统按 "
                        + "Σ(得分×权重)+加分−扣分 计算，封顶 120、封底 0。",
                "5. 结果导入只用于已结束周期的历史迁移；进行中的周期请走正常评分流程。");
        ExcelExportUtil.writeBlankTemplate(response,
                "绩效结果导入模板-" + cycle.getCycleName() + ".xlsx", "绩效结果", headers, notes);
    }

    // ==================================================================
    // 导入
    // ==================================================================

    public PerfImportResultVO importResults(Long cycleId, MultipartFile file) {
        PerfCycle cycle = loader.requireCycle(cycleId);
        assertImportable(cycle);
        List<PerfImportTemplate.PlanLink> links = loader.loadLinks(cycle.getPlanId());
        if (links.isEmpty()) {
            throw new BizException(ErrorCode.PERF_IMPORT_NO_INDICATOR);
        }
        Map<Long, PerfIndicator> indicators = loader.loadIndicators(links);
        List<PerfPlanGrade> grades = loader.loadGrades(cycle.getPlanId());
        if (grades.isEmpty()) {
            throw new BizException(ErrorCode.PERF_GRADE_MISSING);
        }

        List<List<String>> rows = ExcelImportUtil.readFirstSheet(file, MAX_ROWS);
        PerfImportTemplate.Layout layout = PerfImportTemplate.resolve(rows.get(0), links, indicators);
        assertWeightCovered(layout);

        PerfImportResultVO result = new PerfImportResultVO();
        Map<String, User> users = loader.loadUsers(rows, layout);
        Map<Long, PerfSheet> existing = loader.loadSheets(cycleId, users.values());
        Map<String, PerfSheetItem> itemIndex = loader.loadItems(existing.values());

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (ExcelImportUtil.isBlankRow(row)) {
                continue;
            }
            int rowNo = i + 1;
            result.setTotal(result.getTotal() + 1);
            try {
                importOne(cycle, row, layout, users, existing, itemIndex, indicators, grades, result);
            } catch (BizException e) {
                addError(result, rowNo, e.getMessage());
            }
        }
        log.info("绩效结果导入: cycleId={} total={} imported={} updated={} failed={}",
                cycleId, result.getTotal(), result.getImported(), result.getUpdated(), result.getFailed());
        return result;
    }

    private void importOne(PerfCycle cycle, List<String> row, PerfImportTemplate.Layout layout,
                           Map<String, User> users, Map<Long, PerfSheet> existing,
                           Map<String, PerfSheetItem> itemIndex, Map<Long, PerfIndicator> indicators,
                           List<PerfPlanGrade> grades, PerfImportResultVO result) {
        String workNo = ExcelImportUtil.cellAt(row, layout.workNoIndex());
        if (workNo.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "工号为空");
        }
        User user = users.get(workNo);
        if (user == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "工号 " + workNo + " 在系统中不存在");
        }

        // 先把该行所有指标分读出来，任何一格非法就整行不写
        Map<Long, BigDecimal> scores = new LinkedHashMap<>();
        int blanks = 0;
        for (PerfImportTemplate.Column column : layout.indicators()) {
            String text = ExcelImportUtil.cellAt(row, column.index());
            if (text.isBlank()) {
                blanks++;
                continue;
            }
            scores.put(column.indicatorId(), parseScore(text, column.indicatorName()));
        }
        if (blanks == layout.indicators().size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "所有指标都为空");
        }

        PerfSheet sheet = existing.get(user.getId());
        List<PerfSheetItem> items = writer.buildItems(sheet, layout, scores, itemIndex, indicators);
        BigDecimal computed = PerfScoreCalculator.total(items, false);
        BigDecimal finalScore = PerfScoreCalculator.clamp(
                crossCheckTotal(ExcelImportUtil.cellAt(row, layout.totalIndex()), computed));
        PerfPlanGrade grade = PerfScoreCalculator.gradeOf(finalScore, grades);
        String comment = layout.commentIndex() >= 0
                ? ExcelImportUtil.cellAt(row, layout.commentIndex()) : "";

        writer.persist(cycle, user, sheet, items, finalScore, grade, comment);
        result.setImported(result.getImported() + 1);
        if (sheet != null) {
            result.setUpdated(result.getUpdated() + 1);
        }
    }

    // ==================================================================
    // 校验
    // ==================================================================

    /**
     * 总分交叉校验：模板里的「总分」填了就比对，不填就用计算值。
     *
     * <p>为什么要比对：历史数据常从纸质台账抄来，抄错一位数时若只信计算值，
     * 等于悄悄把真实分数改掉了；反之只信填写值，则明细与总分不符也无人发现。</p>
     */
    private BigDecimal crossCheckTotal(String provided, BigDecimal computed) {
        if (provided == null || provided.isBlank()) {
            return computed;
        }
        BigDecimal value = parseNumber(provided, "总分");
        if (value.compareTo(PerfScoreCalculator.MAX_TOTAL) > 0) {
            throw new BizException(ErrorCode.PERF_SCORE_OUT_OF_RANGE,
                    "总分 " + value.setScale(1, RoundingMode.HALF_UP).toPlainString()
                            + " 超出上限 " + PerfScoreCalculator.MAX_TOTAL.toPlainString());
        }
        if (value.subtract(computed).abs().compareTo(TOTAL_TOLERANCE) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "总分与明细不一致：填写 " + value.toPlainString()
                            + "，按明细计算 " + computed.toPlainString());
        }
        return value;
    }

    /**
     * 只允许已结束的周期。
     *
     * <p>判据取「已归档」或「结束日期已过」：后者覆盖那些忘了点归档的老周期。
     * 进行中的周期一律拒绝——否则导入等于绕过四道评分门槛。</p>
     */
    private void assertImportable(PerfCycle cycle) {
        boolean archived = cycle.getStage() != null && cycle.getStage() == PerfConstants.STAGE_ARCHIVED;
        boolean ended = cycle.getEndDate() != null && cycle.getEndDate().isBefore(LocalDate.now());
        if (!archived && !ended) {
            throw new BizException(ErrorCode.PERF_IMPORT_CYCLE_ACTIVE);
        }
    }

    /**
     * 模板的指标列必须覆盖方案全部普通指标（权重合计 100%）。
     *
     * <p>少一列不会报错，但会让所有人的分数系统性偏低——这类错误在导入当时看不出来，
     * 要等发工资才发现，所以宁可整份拒绝。</p>
     */
    private void assertWeightCovered(PerfImportTemplate.Layout layout) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PerfImportTemplate.Column column : layout.indicators()) {
            int type = column.indicatorType() == null ? 1 : column.indicatorType();
            if (type != PerfConstants.INDICATOR_BONUS && type != PerfConstants.INDICATOR_PENALTY) {
                sum = sum.add(column.weight());
            }
        }
        if (sum.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException(ErrorCode.PERF_WEIGHT_NOT_100,
                    "模板覆盖的指标权重合计 " + sum.stripTrailingZeros().toPlainString()
                            + "%，需为 100%（是否漏列了指标？）");
        }
    }

    /** 指标得分：必填，0~100（加分项/扣分项也填正数，方向由指标类型决定） */
    private BigDecimal parseScore(String text, String indicatorName) {
        BigDecimal value = parseNumber(text, indicatorName);
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException(ErrorCode.PERF_SCORE_OUT_OF_RANGE,
                    indicatorName + " 得分 " + value.toPlainString() + " 超出 0~100");
        }
        return value;
    }

    private BigDecimal parseNumber(String text, String label) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PERF_SCORE_OUT_OF_RANGE, label + " 不是合法数字：" + text);
        }
    }

    private void addError(PerfImportResultVO result, int rowNo, String message) {
        result.setFailed(result.getFailed() + 1);
        if (result.getErrors().size() < MAX_ERROR_ROWS) {
            result.getErrors().add(new PerfImportResultVO.RowError(rowNo, message));
        }
    }

    private static String weightText(BigDecimal weight) {
        return weight == null ? "0" : weight.stripTrailingZeros().toPlainString();
    }
}
