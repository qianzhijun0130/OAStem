package com.kellen.oa_system.system.support;

import com.kellen.oa_system.common.util.IdCardUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入相关的纯规则测试：试用期法定上限、身份证校验、新旧模板识别。
 *
 * <p>这几条规则都是「算错了不会报错，只会产生错数据」的类型：
 * 试用期多算一个月、身份证末位校验放过、旧模板被当成新模板——没有一条会抛异常。
 * 所以必须用断言钉住，而不是靠人工点一遍。</p>
 */
class UserImportRuleTest {

    // ------------------------------------------------------------------
    // 试用期上限（《劳动合同法》第十九条）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("试用期上限按合同期限分档：<3个月不得约定，3~12月1个月，1~3年2个月，3年以上6个月")
    void probationCeilingByTerm() {
        int fixed = UserContractRules.TYPE_FIXED;
        assertEquals(0, UserContractRules.probationCeiling(fixed,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)), "3 个月合同不得约定试用期");
        assertEquals(1, UserContractRules.probationCeiling(fixed,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1)), "8 个月合同最多 1 个月");
        assertEquals(2, UserContractRules.probationCeiling(fixed,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)), "1 年合同最多 2 个月");
        assertEquals(2, UserContractRules.probationCeiling(fixed,
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 6, 1)), "2 年半合同最多 2 个月");
        assertEquals(6, UserContractRules.probationCeiling(fixed,
                LocalDate.of(2026, 1, 1), LocalDate.of(2029, 1, 1)), "3 年合同最多 6 个月");
    }

    @Test
    @DisplayName("无固定期限按最高档；实习/劳务/返聘不设试用期")
    void probationCeilingByType() {
        assertEquals(6, UserContractRules.probationCeiling(UserContractRules.TYPE_OPEN,
                LocalDate.of(2026, 1, 1), null));
        // 实习协议不属于劳动合同关系，套用试用期条款反而是风险
        assertEquals(0, UserContractRules.probationCeiling(UserContractRules.TYPE_INTERN,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)));
        assertEquals(0, UserContractRules.probationCeiling(UserContractRules.TYPE_LABOR,
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1)));
    }

    @Test
    @DisplayName("留空时默认 3 个月，但不超过法定上限")
    void defaultProbation() {
        assertEquals(0, UserContractRules.defaultProbation(0), "短合同默认 0，否则默认值本身违法");
        assertEquals(1, UserContractRules.defaultProbation(1));
        assertEquals(2, UserContractRules.defaultProbation(2));
        assertEquals(3, UserContractRules.defaultProbation(6));
    }

    @Test
    @DisplayName("试用期截止日 = 入职日 + 月数 − 1 天（不是 + 月数）")
    void probationEnd() {
        // 3 个月试用从 1 月 1 日起，到 3 月 31 日止；写成 4 月 1 日会让转正提醒晚一天
        assertEquals(LocalDate.of(2026, 3, 31),
                UserContractRules.probationEnd(LocalDate.of(2026, 1, 1), 3));
        assertEquals(LocalDate.of(2027, 1, 31),
                UserContractRules.probationEnd(LocalDate.of(2027, 1, 1), 1));
        assertNull(UserContractRules.probationEnd(LocalDate.of(2026, 1, 1), 0), "0 个月不产生截止日");
        assertNull(UserContractRules.probationEnd(null, 3));
    }

    // ------------------------------------------------------------------
    // 身份证
    // ------------------------------------------------------------------

    @Test
    @DisplayName("身份证校验：长度、字符、出生日期、校验码缺一不可")
    void idCardValidation() {
        // 11010119900307 + 123 + 校验码 3
        assertTrue(IdCardUtil.isValid("110101199003071233"));
        assertTrue(IdCardUtil.isValid("11010119900307123X") == false, "校验码不对必须拒绝");

        assertFalse(IdCardUtil.isValid("11010119900307123"), "17 位");
        assertFalse(IdCardUtil.isValid("11010119900307123A"), "末位非数字也非 X");
        assertFalse(IdCardUtil.isValid("110101199013071233"), "13 月");
        assertFalse(IdCardUtil.isValid(null));
        assertFalse(IdCardUtil.isValid(""));
    }

    @Test
    @DisplayName("身份证派生：生日、性别、脱敏")
    void idCardDerived() {
        assertEquals(LocalDate.of(1990, 3, 7), IdCardUtil.birthDateOf("110101199003071233"));
        // 第 17 位 3 为奇数 → 男
        assertEquals(1, IdCardUtil.genderOf("110101199003071233"));
        assertEquals("110101********1233", IdCardUtil.mask("110101199003071233"));
    }

    @Test
    @DisplayName("盲索引的规范化：去空白、字母大写（摘要本身见 AesCryptoTest）")
    void idCardNormalized() {
        // 摘要计算要拿 Spring 装配好的密钥实例，属于加密模块的测试（AesCryptoTest）；
        // 这里只验「送进摘要之前」的规范化，它同样决定了同一个号会不会被判成两人
        assertEquals("11010119900307123X", IdCardUtil.normalize(" 11010119900307123x "));
        assertEquals(IdCardUtil.normalize("11010119900307123x"),
                IdCardUtil.normalize("11010119900307123X"));
    }

    // ------------------------------------------------------------------
    // 模板
    // ------------------------------------------------------------------

    @Test
    @DisplayName("表头 18 列且顺序固定")
    void headers() {
        List<String> headers = UserImportTemplate.headers();
        assertEquals(18, headers.size(), "10 列基础 + 8 列建档（合同起止必须拆两列）");
        assertEquals("身份证号", headers.get(UserImportTemplate.COL_ID_CARD));
        assertEquals("任职状态", headers.get(UserImportTemplate.COL_EMPLOYMENT_STATUS));
    }

    @Test
    @DisplayName("旧 10 列模板被识别为旧版而不是「表头错误」")
    void legacyHeaderDetected() {
        List<String> legacy = UserImportTemplate.headers().subList(0, 10);
        assertTrue(UserImportTemplate.looksLikeLegacy(legacy),
                "旧模板必须给出「请下载新模板」的提示，报第 11 列表头错误只会让 HR 以为系统坏了");

        // 旧模板右侧多加了备注列，仍算旧模板
        List<String> legacyWithExtra = new java.util.ArrayList<>(legacy);
        legacyWithExtra.add("备注");
        assertTrue(UserImportTemplate.looksLikeLegacy(legacyWithExtra));

        assertFalse(UserImportTemplate.looksLikeLegacy(UserImportTemplate.headers()));
    }
}
