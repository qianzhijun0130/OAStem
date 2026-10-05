package com.kellen.oa_system.system.service.impl;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.util.UserAgentUtil;
import com.kellen.oa_system.security.JwtUtil;
import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.system.dto.LoginRequest;
import com.kellen.oa_system.system.entity.LoginLog;
import com.kellen.oa_system.system.mapper.LoginLogMapper;
import com.kellen.oa_system.system.service.AuthService;
import com.kellen.oa_system.system.service.UserService;
import com.kellen.oa_system.system.vo.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现。
 *
 * <p>安全设计：
 * <ol>
 *   <li>密码比对完全交给 Spring Security，业务代码不接触明文比较；</li>
 *   <li>对「用户不存在」与「密码错误」返回同一错误码，避免暴露账号是否存在；</li>
 *   <li>登录日志写入失败不能影响登录主流程，因此单独 try-catch。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final LoginLogMapper loginLogMapper;

    @Override
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String username = request.getUsername();
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();

            String token = jwtUtil.generateToken(loginUser);
            userService.updateLoginInfo(loginUser.getUserId(), ip);
            recordLoginLog(loginUser.getUserId(), username, ip, userAgent, 1, null);

            return LoginResponse.builder()
                    .token(token)
                    .tokenType(TOKEN_TYPE)
                    .expiresIn(jwtUtil.getExpirationMillis())
                    .username(loginUser.getUsername())
                    .realName(resolveRealName(loginUser.getUserId()))
                    .build();
        } catch (DisabledException e) {
            recordLoginLog(null, username, ip, userAgent, 0, "账号已禁用");
            throw new BizException(ErrorCode.USER_DISABLED);
        } catch (LockedException e) {
            recordLoginLog(null, username, ip, userAgent, 0, "账号已锁定");
            throw new BizException(ErrorCode.USER_LOCKED);
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            recordLoginLog(null, username, ip, userAgent, 0, "用户名或密码错误");
            throw new BizException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
    }

    private String resolveRealName(Long userId) {
        try {
            return userService.getById(userId) == null ? null : userService.getById(userId).getRealName();
        } catch (Exception e) {
            log.warn("查询用户真实姓名失败, userId={}", userId, e);
            return null;
        }
    }

    private void recordLoginLog(Long userId, String username, String ip, String userAgent,
                                Integer status, String failReason) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(username);
            loginLog.setLoginType(1);
            loginLog.setIpAddress(ip);
            loginLog.setBrowser(UserAgentUtil.resolveBrowser(userAgent));
            loginLog.setOs(UserAgentUtil.resolveOs(userAgent));
            loginLog.setLoginStatus(status);
            loginLog.setFailReason(failReason);
            // location（IP 归属地）需要外部 IP 库或三方接口，本项目不引入，
            // 保持为空而不是填一个假值，避免排查时被误导
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            // 日志写入失败不能阻断登录
            log.error("写入登录日志失败, username={}", username, e);
        }
    }
}
