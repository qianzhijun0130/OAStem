package com.kellen.oa_system.common.util;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User-Agent 解析工具：从请求头中提取浏览器与操作系统，供登录日志留痕。
 *
 * <p>为什么手写字符串匹配而不引第三方库：登录日志只需要「大致是谁、用什么端」这两个粗粒度信息，
 * 引入 UA 解析库（ua-parser 等）会带来一个只为写日志服务的运行时依赖。匹配顺序是有讲究的——
 * Edge 的 UA 里同时含 {@code Chrome} 与 {@code Safari}，必须先判 Edge，否则永远识别成 Chrome。</p>
 */
public final class UserAgentUtil {

    private static final String UNKNOWN = "未知";

    /** 按顺序匹配，先命中者胜（顺序即优先级：派生浏览器必须排在父级之前） */
    private static final Map<String, String> BROWSER_RULES = new LinkedHashMap<>();
    private static final Map<String, String> OS_RULES = new LinkedHashMap<>();

    static {
        BROWSER_RULES.put("Edg/", "Edge");
        BROWSER_RULES.put("EdgA/", "Edge");
        BROWSER_RULES.put("OPR/", "Opera");
        BROWSER_RULES.put("Firefox/", "Firefox");
        BROWSER_RULES.put("MicroMessenger", "微信内置浏览器");
        BROWSER_RULES.put("Chrome/", "Chrome");
        BROWSER_RULES.put("Safari/", "Safari");

        OS_RULES.put("Windows NT 10", "Windows 10/11");
        OS_RULES.put("Windows NT", "Windows");
        OS_RULES.put("Android", "Android");
        OS_RULES.put("iPhone", "iOS");
        OS_RULES.put("iPad", "iPadOS");
        OS_RULES.put("Mac OS X", "macOS");
        OS_RULES.put("Linux", "Linux");
    }

    private UserAgentUtil() {
    }

    public static String resolveBrowser(String userAgent) {
        return match(userAgent, BROWSER_RULES);
    }

    public static String resolveOs(String userAgent) {
        return match(userAgent, OS_RULES);
    }

    private static String match(String userAgent, Map<String, String> rules) {
        if (!StringUtils.hasText(userAgent)) {
            return UNKNOWN;
        }
        for (Map.Entry<String, String> rule : rules.entrySet()) {
            if (userAgent.contains(rule.getKey())) {
                return rule.getValue();
            }
        }
        return "其他";
    }
}
