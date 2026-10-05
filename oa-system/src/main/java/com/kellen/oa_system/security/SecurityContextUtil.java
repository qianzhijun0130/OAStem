package com.kellen.oa_system.security;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 获取当前登录用户的统一入口。
 *
 * <p>各业务模块不要直接操作 SecurityContextHolder，统一走这里，
 * 避免重复的空判断与类型判断散落各处。</p>
 */
public final class SecurityContextUtil {

    private SecurityContextUtil() {
    }

    public static LoginUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getUserId();
    }
}
