package com.kellen.oa_system.system.service;

import com.kellen.oa_system.system.dto.LoginRequest;
import com.kellen.oa_system.system.vo.LoginResponse;

/**
 * 认证服务。
 */
public interface AuthService {

    /**
     * 账号密码登录。
     *
     * @param request   登录参数
     * @param ip        客户端 IP，用于记录登录日志
     * @param userAgent 请求头 User-Agent，用于解析浏览器与操作系统写入登录日志
     */
    LoginResponse login(LoginRequest request, String ip, String userAgent);
}
