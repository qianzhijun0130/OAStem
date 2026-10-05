package com.kellen.oa_system.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 客户端 IP 获取工具。
 *
 * <p>经过 Nginx 等反向代理时，真实 IP 在 X-Forwarded-For 的第一个节点；
 * 直接取 remoteAddr 会拿到网关地址，导致登录日志失去溯源价值。</p>
 */
public final class IpUtil {

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
    };

    private IpUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value)) {
                // X-Forwarded-For 可能是 "client, proxy1, proxy2"
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
