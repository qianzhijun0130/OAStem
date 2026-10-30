package com.kellen.oa_system.hr.performance.support;

import com.kellen.oa_system.hr.performance.entity.PerfCycle;
import com.kellen.oa_system.hr.performance.entity.PerfIndicator;
import com.kellen.oa_system.hr.performance.entity.PerfPlanGrade;
import com.kellen.oa_system.hr.performance.entity.PerfSheet;
import com.kellen.oa_system.hr.performance.entity.PerfSheetItem;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetItemMapper;
import com.kellen.oa_system.hr.performance.mapper.PerfSheetMapper;
import com.kellen.oa_system.system.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 绩效导入的写入层：指标行构造 + 考核表与指标行落库。
 *
 * <p>单独成类是因为「结构必须与正常流程一致」这条最容易在改动中丢失：新建考核表时
 * 指标行的名称/权重/类型要从方案复制，而不是信 Excel 的表头——表头只是给人看的位置索引。
 * 一旦某处写成「按 Excel 的权重入库」，同一个指标在两个人身上会有两个权重，
 * 分布统计与导出都会对不上。</p>
 */
@Component
@RequiredArgsConstructor
public class PerfImportWriter {

    private final PerfSheetMapper sheetMapper;
    private final PerfSheetItemMapper sheetItemMapper;

    /**
     * 按方案指标生成该行的指标行（只含本次有分数的指标）。
     *
     * @param sheet 已有考核表；null 表示需要新建
     */
    public List<PerfSheetItem> buildItems(PerfSheet sheet, PerfImportTemplate.Layout layout,
                                          Map<Long, BigDecimal> scores,
                                          Map<String, PerfSheetItem> itemIndex,
                                          Map<Long, PerfIndicator> indicators) {
        List<PerfSheetItem> items = new ArrayList<>(layout.indicators().size());
        int sort = 0;
        for (PerfImportTemplate.Column column : layout.indicators()) {
            sort++;
            BigDecimal score = scores.get(column.indicatorId());
            if (score == null) {
                continue;
            }
            PerfSheetItem item = sheet == null ? null
                    : itemIndex.get(sheet.getId() + "|" + column.indicatorId());
            if (item == null) {
                // 新建考核表，或该指标不在这人已有的考核表里（方案中途加过指标）——补一行，
                // 而不是静默丢掉：丢掉会让这一列分数永远进不了总分
                item = new PerfSheetItem();
                PerfIndicator indicator = indicators.get(column.indicatorId());
                item.setIndicatorId(column.indicatorId());
                item.setIndicatorCode(indicator == null || indicator.getIndicatorCode() == null
                        ? "IMPORT" : indicator.getIndicatorCode());
                item.setIndicatorName(column.indicatorName());
                item.setIndicatorType(column.indicatorType());
                item.setWeight(column.weight());
                item.setSort(sort);
            }
            item.setManagerScore(score);
            item.setFinalScore(score);
            items.add(item);
        }
        return items;
    }

    /**
     * 落库：无考核表则新建，有则更新。
     *
     * <p>五个状态一次性置为「已完成」——导入的就是终态，留一个 0 会让详情页显示成「还没评」。
     * {@code selfTotal} 刻意留空而不是填 0：填 0 会被读成「自评给了 0 分」。</p>
     */
    public void persist(PerfCycle cycle, User user, PerfSheet sheet, List<PerfSheetItem> items,
                        BigDecimal finalScore, PerfPlanGrade grade, String comment) {
        boolean created = sheet == null;
        PerfSheet target = created ? new PerfSheet() : sheet;
        if (created) {
            target.setCycleId(cycle.getId());
            target.setUserId(user.getId());
            target.setDeptId(user.getDeptId());
            // 导入的是历史结果，不需要上级再评分；leaderId 只用于展示归属
            target.setLeaderId(user.getDirectLeaderId());
            target.setPlanId(cycle.getPlanId());
            target.setSelfMissing(0);
            target.setHrScored(0);
        }
        target.setTargetStatus(2);
        target.setSelfStatus(1);
        target.setManagerStatus(1);
        target.setCalibrateStatus(1);
        target.setConfirmStatus(1);
        target.setManagerTotal(finalScore);
        target.setTotalScore(finalScore);
        target.setGradeCode(grade == null ? null : grade.getGradeCode());
        target.setCoefficient(grade == null ? BigDecimal.ONE : grade.getCoefficient());
        target.setSource(PerfConstants.SOURCE_IMPORT);
        if (comment != null && !comment.isBlank()) {
            target.setManagerComment(comment.length() > 1000 ? comment.substring(0, 1000) : comment);
        }
        if (created) {
            sheetMapper.insert(target);
        } else {
            sheetMapper.updateById(target);
        }

        for (PerfSheetItem item : items) {
            if (item.getId() == null) {
                item.setSheetId(target.getId());
                sheetItemMapper.insert(item);
            } else {
                // 只回填分数，不动指标名/权重（那是方案的结构，不该被 Excel 改）
                PerfSheetItem patch = new PerfSheetItem();
                patch.setId(item.getId());
                patch.setManagerScore(item.getManagerScore());
                patch.setFinalScore(item.getFinalScore());
                sheetItemMapper.updateById(patch);
            }
        }
    }
}
