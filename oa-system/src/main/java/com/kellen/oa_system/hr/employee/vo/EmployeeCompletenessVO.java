package com.kellen.oa_system.hr.employee.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 建档完整度看板（BIZ-OPT-09）。
 *
 * <p>为什么需要这个看板：系统刚上线时档案普遍是空的——没身份证、没合同、
 * 没参加工作日期。这些缺失不会让任何功能立刻报错，而是几个月后集中爆发：
 * 社保报送缺号、年假算不出来、合同到期没人提醒、发工资缺银行卡。
 * 一张「还缺什么、缺多少人」的卡片，是把这些隐患变成可执行清单的唯一方式。</p>
 */
@Data
@Builder
public class EmployeeCompletenessVO {

    /** 在职员工总数（口径 employment_status IN (2,3,4)），作为分母 */
    private Long totalOnDuty;

    /** 六个维度（顺序固定，前端按此渲染卡片） */
    private List<Card> cards;

    /**
     * 一个维度一张卡片。
     */
    @Data
    @Builder
    public static class Card {

        /** 维度编码，点击卡片跳清单时原样回传 */
        private String dimension;

        /** 维度名称，如「无在职合同」 */
        private String label;

        /** 该维度缺失的人数 */
        private Long count;

        /** 占在职人数的比例（0~100，保留一位小数）；在职人数为 0 时为 0 */
        private Double percent;
    }
}
