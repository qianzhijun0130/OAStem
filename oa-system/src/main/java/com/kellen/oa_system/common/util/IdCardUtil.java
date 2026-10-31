package com.kellen.oa_system.common.util;

import com.kellen.oa_system.security.crypto.AesCrypto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 身份证号的格式校验与派生信息。
 *
 * <p>为什么单独成类：身份证号在本系统有四个使用点——员工档案编辑、批量导入、
 * 查重、脱敏展示。校验规则（18 位 + 校验码 + 出生日期合法）只要有一处写松，
 * 脏数据就从那条缝进来，之后所有依赖它的地方（生日提醒、退休预测、社保报送）
 * 都要各自兜底。所以规则只放这里一份。</p>
 *
 * <p>与加密的关系：本类只处理<b>明文</b>。落库时由 {@code User#idCard} 的
 * TypeHandler 负责加密；查重所需的确定性摘要见 {@link #hash(String)}。</p>
 */
public final class IdCardUtil {

    /** 18 位：6 位地址 + 8 位生日 + 3 位顺序 + 1 位校验（末位可为 X） */
    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private static final DateTimeFormatter BIRTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private IdCardUtil() {
    }

    /**
     * 格式校验：长度、字符、出生日期、校验码。
     *
     * <p>只做「是不是一个身份证号」，不查真伪（那要公安接口）。
     * 校验码不验证的后果很实际：手工录入错一位数字，系统照单全收，
     * 等到社保报送被退回时已过了几个月，还得回头找每个人核对。</p>
     */
    public static boolean isValid(String idCard) {
        if (idCard == null) {
            return false;
        }
        String value = normalize(idCard);
        if (value.length() != 18) {
            return false;
        }
        for (int i = 0; i < 17; i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        char last = value.charAt(17);
        if (!Character.isDigit(last) && last != 'X') {
            return false;
        }
        if (birthDateOf(value) == null) {
            return false;
        }
        return last == checkCodeOf(value);
    }

    /** 去掉空白并把末位 x 统一成大写 X；不做长度或格式修正 */
    public static String normalize(String idCard) {
        return idCard == null ? null : AesCrypto.normalizeForIndex(idCard);
    }

    /**
     * 出生日期（前 14 位 yyyyMMdd）。非法日期返回 null。
     */
    public static LocalDate birthDateOf(String idCard) {
        String value = normalize(idCard);
        if (value == null || value.length() < 14) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.substring(6, 14), BIRTH_FORMAT);
            // 明显不合理的年份直接判非法：1900 年前的人不会来入职，未来日期更不可能
            if (date.getYear() < 1900 || date.isAfter(LocalDate.now())) {
                return null;
            }
            return date;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 性别（第 17 位奇数为男）。与 {@code GenderEnum} 口径一致：1 男 2 女。
     */
    public static Integer genderOf(String idCard) {
        String value = normalize(idCard);
        if (value == null || value.length() != 18) {
            return null;
        }
        char c = value.charAt(16);
        if (!Character.isDigit(c)) {
            return null;
        }
        return (c - '0') % 2 == 1 ? 1 : 2;
    }

    /**
     * 盲索引：用于「是否已建档」的等值查重。
     *
     * <p>委托 {@link AesCrypto#blindIndex(String)}——密钥与加密共用一套，
     * 换密钥时密文与哈希会一起失效，迁移流程只有一条，不会漏。</p>
     */
    public static String hash(String idCard) {
        String value = normalize(idCard);
        return value == null || value.isEmpty() ? null : AesCrypto.get().blindIndex(value);
    }

    /** 脱敏展示：保留前 6 位与后 4 位，如 110101********1234 */
    public static String mask(String idCard) {
        String value = normalize(idCard);
        if (value == null || value.length() < 10) {
            return value == null ? null : "****";
        }
        int keepTail = 4;
        return value.substring(0, 6)
                + "*".repeat(Math.max(0, value.length() - 6 - keepTail))
                + value.substring(value.length() - keepTail);
    }

    private static char checkCodeOf(String value) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (value.charAt(i) - '0') * WEIGHTS[i];
        }
        return CHECK_CODES[sum % 11];
    }
}
