package com.kellen.oa_system.system.support;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 合同类型与试用期的法定规则（纯函数，无依赖）。
 *
 * <p>为什么把这几条规则单独拎出来：试用期上限不是「配置项」而是法定约束，
 * 一旦写松，导入进来的是一批法律上站不住的合同——而系统里看不出任何异常，
 * 直到发生劳动争议才发现。这类规则必须只有一份，且可被单测直接覆盖。</p>
 *
 * <p>依据《劳动合同法》第十九条：</p>
 * <ul>
 *   <li>合同期限 3 个月以上不满 1 年 → 试用期不得超过 1 个月；</li>
 *   <li>1 年以上不满 3 年 → 不得超过 2 个月；</li>
 *   <li>3 年以上固定期限及无固定期限 → 不得超过 6 个月；</li>
 *   <li>以完成一定工作任务为期限、或期限不满 3 个月 → 不得约定试用期。</li>
 * </ul>
 */
public final class UserContractRules {

    public static final int TYPE_FIXED = 1;
    public static final int TYPE_OPEN = 2;
    public static final int TYPE_INTERN = 3;
    public static final int TYPE_LABOR = 4;
    public static final int TYPE_REHIRE = 5;

    /** 无固定期限合同不设结束日，试用期按最高档（6 个月）判定 */
    private static final int OPEN_ENDED_CEILING = 6;

    /** 留空时的默认试用期：不超过 3 个月，且不超过法定上限 */
    private static final int DEFAULT_PROBATION = 3;

    private UserContractRules() {
    }

    /**
     * 合同类型中文 → 编码。无法识别返回 null（调用方据此报错）。
     */
    public static Integer parseType(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        return switch (value) {
            case "固定期限", "固定期", "1" -> TYPE_FIXED;
            case "无固定期限", "无固定期", "长期", "2" -> TYPE_OPEN;
            case "实习", "实习协议", "3" -> TYPE_INTERN;
            case "劳务", "劳务合同", "4" -> TYPE_LABOR;
            case "返聘", "5" -> TYPE_REHIRE;
            default -> null;
        };
    }

    public static String typeLabel(Integer type) {
        return switch (type == null ? 0 : type) {
            case TYPE_FIXED -> "固定期限";
            case TYPE_OPEN -> "无固定期限";
            case TYPE_INTERN -> "实习";
            case TYPE_LABOR -> "劳务";
            case TYPE_REHIRE -> "返聘";
            default -> "未知";
        };
    }

    /**
     * 该合同的试用期法定上限（月）。
     *
     * <p>实习/劳务/返聘不属于劳动合同关系，不套用《劳动合同法》的试用期条款，
     * 按「不设试用期」处理（返回 0）。若这几类也允许填试用期，等于把
     * 「实习协议」当成劳动合同用，反而是风险。</p>
     *
     * @param type  合同类型
     * @param start 开始日期
     * @param end   结束日期，可为 null（仅无固定期限允许为 null）
     */
    public static int probationCeiling(Integer type, LocalDate start, LocalDate end) {
        if (type == null) {
            return OPEN_ENDED_CEILING;
        }
        if (type == TYPE_INTERN || type == TYPE_LABOR || type == TYPE_REHIRE) {
            return 0;
        }
        if (type == TYPE_OPEN || end == null) {
            return OPEN_ENDED_CEILING;
        }
        if (start == null) {
            return OPEN_ENDED_CEILING;
        }
        long months = contractMonths(start, end);
        if (months < 3) {
            return 0;
        }
        if (months < 12) {
            return 1;
        }
        if (months < 36) {
            return 2;
        }
        return OPEN_ENDED_CEILING;
    }

    /**
     * 未填写试用期月数时的默认值。
     *
     * <p>取「3 个月」与「法定上限」的较小值：HR 大多数场景就是三个月，
     * 但短合同必须自动降档，否则默认值本身就地违法。</p>
     */
    public static int defaultProbation(Integer ceiling) {
        return Math.min(DEFAULT_PROBATION, Math.max(0, ceiling));
    }

    /**
     * 试用期截止日 = 入职日 + 月数 − 1 天。
     *
     * <p>减 1 天是业务口径（BIZ-OPT-01 同源）：3 个月试用期从 1 月 1 日起，
     * 到 3 月 31 日止，而不是 4 月 1 日。差这一天会让「转正提醒」晚一天发，
     * 也会让试用期在月末边界上多算一天薪资。</p>
     */
    public static LocalDate probationEnd(LocalDate entryDate, Integer months) {
        if (entryDate == null || months == null || months <= 0) {
            return null;
        }
        return entryDate.plusMonths(months).minusDays(1);
    }

    /** 合同期限（整月数，向下取整） */
    public static long contractMonths(LocalDate start, LocalDate end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0;
        }
        return ChronoUnit.MONTHS.between(start, end);
    }
}
