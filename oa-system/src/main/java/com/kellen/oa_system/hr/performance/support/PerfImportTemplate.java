package com.kellen.oa_system.hr.performance.support;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.performance.entity.PerfIndicator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 绩效结果导入模板的列定义与解析。
 *
 * <p>模板是<b>按周期</b>生成的：每个方案配的指标不同，所以列不是固定的。
 * 表头形如 {@code 指标名(20%)}——括号里的权重不是给人看的装饰，而是<b>校验用的指纹</b>：
 * 若文件里的权重与方案当前配置不一致，说明方案改过（或文件是旧模板），
 * 此时导入会把分数按错误的权重折算，整份文件直接拒绝比逐行报错更清楚。</p>
 *
 * <p>没有括号的列也能用（HR 手工做的表），此时权重按方案取、不做指纹校验。</p>
 */
public final class PerfImportTemplate {

    /** 工号、姓名两个固定列 */
    public static final String COL_WORK_NO = "工号";
    public static final String COL_NAME = "姓名";
    /** 评语选填 */
    public static final String COL_COMMENT = "评语";
    /**
     * 总分（选填）。
     *
     * <p>总分本来就由「Σ(得分×权重)+加分−扣分」唯一确定，让 HR 再填一遍是为了<b>交叉校验</b>：
     * 历史数据常从纸质台账抄来，抄错一位数时如果只信计算值就会悄悄改掉真实分数。
     * 填了就比对，差超过 0.1 该行失败；不填就用计算值。</p>
     */
    public static final String COL_TOTAL = "总分";

    private PerfImportTemplate() {
    }

    /** 一列 = 一个方案指标 */
    public record Column(int index, Long indicatorId, String indicatorName, Integer indicatorType,
                         BigDecimal weight) {
    }

    /** 解析结果：固定列下标 + 指标列 */
    public record Layout(int workNoIndex, int nameIndex, int commentIndex, int totalIndex,
                         List<Column> indicators) {
    }

    /**
     * 由表头解析列布局。
     *
     * @param header       表头（第一行）
     * @param planLinks    方案指标关联（indicatorId → weight），由调用方按 sort 排好
     * @param indicators   indicatorId → 指标实体（取名称与类型）
     */
    public static Layout resolve(List<String> header, List<PlanLink> planLinks,
                                 Map<Long, PerfIndicator> indicators) {
        Map<String, PlanLink> byName = new LinkedHashMap<>();
        for (PlanLink link : planLinks) {
            PerfIndicator indicator = indicators.get(link.indicatorId());
            if (indicator != null && indicator.getIndicatorName() != null) {
                byName.put(normalize(indicator.getIndicatorName()), link);
            }
        }

        int workNoIndex = -1;
        int nameIndex = -1;
        int commentIndex = -1;
        int totalIndex = -1;
        List<Column> columns = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            String raw = header.get(i) == null ? "" : header.get(i).trim();
            if (raw.isEmpty()) {
                continue;
            }
            String bare = stripWeightSuffix(raw);
            if (bare.equals(COL_WORK_NO) || bare.equals("员工工号")) {
                workNoIndex = i;
                continue;
            }
            if (bare.equals(COL_NAME) || bare.equals("员工姓名")) {
                nameIndex = i;
                continue;
            }
            if (bare.equals(COL_COMMENT) || bare.equals("备注")) {
                commentIndex = i;
                continue;
            }
            if (bare.equals(COL_TOTAL) || bare.equals("总得分") || bare.equals("最终得分")) {
                totalIndex = i;
                continue;
            }
            PlanLink link = byName.get(normalize(bare));
            if (link == null) {
                // 不是固定列也不是本方案的指标：说明文件与周期不匹配，继续导入会把分数写错地方
                throw new BizException(ErrorCode.USER_IMPORT_FILE_INVALID,
                        "表头「" + raw + "」不属于该周期方案的指标，请下载本周期的模板");
            }
            BigDecimal headerWeight = weightOf(raw);
            if (headerWeight != null && link.weight() != null
                    && headerWeight.compareTo(link.weight()) != 0) {
                throw new BizException(ErrorCode.USER_IMPORT_FILE_INVALID,
                        "表头「" + raw + "」的权重与方案当前配置（" + link.weight() + "%）不一致，"
                                + "方案可能已调整，请重新下载模板");
            }
            PerfIndicator indicator = indicators.get(link.indicatorId());
            columns.add(new Column(i, link.indicatorId(), bare,
                    indicator == null || indicator.getIndicatorType() == null
                            ? 1 : indicator.getIndicatorType(),
                    link.weight() == null ? BigDecimal.ZERO : link.weight()));
        }
        if (workNoIndex < 0) {
            // 用通用导入码（1111）而不是考勤的 6209：那是考勤模块的表头码，跨模块借用会让
            // 前端按码分流时对不上模块
            throw new BizException(ErrorCode.USER_IMPORT_FILE_INVALID,
                    "模板缺少「工号」列，请下载本周期的模板");
        }
        if (columns.isEmpty()) {
            throw new BizException(ErrorCode.PERF_IMPORT_NO_INDICATOR);
        }
        return new Layout(workNoIndex, nameIndex, commentIndex, totalIndex, columns);
    }

    /** 方案指标关联的最小信息（不直接依赖实体，便于测试） */
    public record PlanLink(Long indicatorId, BigDecimal weight, Integer sort) {
    }

    /** 去掉 {@code (20%)} 后缀 */
    public static String stripWeightSuffix(String header) {
        return header.replaceAll("[(（]\\s*\\d+(\\.\\d+)?\\s*%\\s*[)）]$", "").trim();
    }

    /** 取括号里的权重；没有则 null */
    public static BigDecimal weightOf(String header) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("[(（]\\s*(\\d+(?:\\.\\d+)?)\\s*%\\s*[)）]").matcher(header);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    private static String normalize(String text) {
        return text.replace(" ", "").replace("　", "").trim();
    }
}
