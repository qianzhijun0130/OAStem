package com.kellen.oa_system.common.util;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 日志脱敏工具。
 *
 * <p>原则：<b>金额、证件号、卡号、手机号一律不得以原文进入应用日志</b>。
 * 日志会被落盘、被采集系统收走、被运维和开发随手 grep——它比数据库更容易泄露，
 * 且通常没有访问审计。所以敏感值在写日志的那一刻就要抹掉，而不是指望"日志文件不外传"。</p>
 *
 * <p>取舍：金额统一输出 {@code ***}，不保留数量级。看似信息量低，但日志里真正需要的是
 * 「有没有值、哪个用户、哪个月」，具体金额应当去数据库查（那里有权限控制与审计）。</p>
 */
public final class LogMasker {

    private static final String MASK = "***";

    private LogMasker() {
    }

    /** 金额：有值即打码，null 保留 null 以便区分「没设置」与「设置了但被打码」 */
    public static String amount(BigDecimal value) {
        return value == null ? "null" : MASK;
    }

    /** 金额变动：保留「从哪个状态到哪个状态」，不暴露数值 */
    public static String amountChange(BigDecimal before, BigDecimal after) {
        return amount(before) + "→" + amount(after);
    }

    /** 身份证：保留前 3 位与后 4 位 */
    public static String idCard(String value) {
        if (!StringUtils.hasText(value) || value.length() < 8) {
            return StringUtils.hasText(value) ? MASK : value;
        }
        return value.substring(0, 3) + "***********" + value.substring(value.length() - 4);
    }

    /** 银行卡：只留后 4 位 */
    public static String bankCard(String value) {
        if (!StringUtils.hasText(value) || value.length() < 4) {
            return StringUtils.hasText(value) ? MASK : value;
        }
        return "****" + value.substring(value.length() - 4);
    }

    /** 手机号：保留前 3 位与后 4 位 */
    public static String mobile(String value) {
        if (!StringUtils.hasText(value) || value.length() < 8) {
            return StringUtils.hasText(value) ? MASK : value;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
