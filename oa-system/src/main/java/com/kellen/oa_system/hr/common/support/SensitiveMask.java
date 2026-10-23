package com.kellen.oa_system.hr.common.support;

import org.springframework.util.StringUtils;

/**
 * 敏感字段脱敏。
 *
 * <p>列表与详情默认返回脱敏值；只有具备 {@code hr:employee} 且是 HR 角色的用户才看得到原文。
 * 脱敏规则固定在这里，避免各页面各写一套正则导致「同一个身份证在两张页面显示不同」。</p>
 */
public final class SensitiveMask {

    private SensitiveMask() {
    }

    /**
     * 手机号：保留前 3 位与后 4 位，如 {@code 138****8000}。
     */
    public static String mobile(String value) {
        if (!StringUtils.hasText(value) || value.length() < 8) {
            return value;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /**
     * 身份证号：保留前 3 位与后 4 位，中间定长星号（不暴露原长度，避免辅助推算）。
     */
    public static String idCard(String value) {
        if (!StringUtils.hasText(value) || value.length() < 8) {
            return value;
        }
        return value.substring(0, 3) + "***********" + value.substring(value.length() - 4);
    }

    /**
     * 银行卡号：只留后 4 位，如 {@code **** **** **** 1234}。
     */
    public static String bankAccount(String value) {
        if (!StringUtils.hasText(value) || value.length() < 4) {
            return value;
        }
        return "**** **** **** " + value.substring(value.length() - 4);
    }
}
