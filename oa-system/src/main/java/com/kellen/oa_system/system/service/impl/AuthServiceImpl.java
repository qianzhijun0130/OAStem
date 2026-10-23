package com.kellen.oa_system.system.service.impl;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.util.UserAgentUtil;
import com.kellen.oa_system.security.JwtUtil;
import com.kellen.oa_system.security.LoginFailureGuard;
import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.security.OnlineUserRegistry;
import com.kellen.oa_system.system.dto.LoginRequest;
import com.kellen.oa_system.system.entity.LoginLog;
import com.kellen.oa_system.system.entity.User;
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
    private final LoginFailureGuard loginFailureGuard;
    private final OnlineUserRegistry onlineUserRegistry;

    @Override
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String username = request.getUsername();
        // 已被限流锁定的账号直接拒绝：既省一次密码哈希运算，也避免继续累加失败计数
        if (loginFailureGuard.isLocked(username)) {
            recordLoginLog(null, username, ip, userAgent, 0, "登录失败次数过多，临时锁定");
            throw new BizException(ErrorCode.LOGIN_TOO_MANY_FAILURES);
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();

            String token = jwtUtil.generateToken(loginUser);
            userService.updateLoginInfo(loginUser.getUserId(), ip);
            recordLoginLog(loginUser.getUserId(), username, ip, userAgent, 1, null);
            // 登记在线会话（TTL = 令牌有效期）：失效即自动从「在线用户」列表消失，无需清理任务。
            // 失败不影响登录本身——在线列表属于旁路能力，不能因为它把登录打断
            onlineUserRegistry.register(loginUser.getUserId(), loginUser.getUsername(),
                    resolveRealName(loginUser.getUserId()), ip, userAgent,
                    jwtUtil.getExpirationMillis() / 1000);
            // 登录成功即清空失败计数，避免正常用户偶发输错被逐步累积到锁定
            loginFailureGuard.onSuccess(username);

            return LoginResponse.builder()
                    .token(token)
                    .tokenType(TOKEN_TYPE)
                    .expiresIn(jwtUtil.getExpirationMillis())
                    .username(loginUser.getUsername())
                    .realName(resolveRealName(loginUser.getUserId()))
                    .build();
        } catch (DisabledException e) {
            // 账号状态问题不计入密码失败次数：它不是「猜密码」，反复试也不会变
            recordLoginLog(null, username, ip, userAgent, 0, "账号已禁用");
            throw new BizException(ErrorCode.USER_DISABLED);
        } catch (LockedException e) {
            recordLoginLog(null, username, ip, userAgent, 0, "账号已锁定");
            throw new BizException(ErrorCode.USER_LOCKED);
        } catch (BadCredentialsException | UsernameNotFoundException e) {
            loginFailureGuard.onFailure(username);
            recordLoginLog(null, username, ip, userAgent, 0, "用户名或密码错误");
            throw new BizException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
    }

    private String resolveRealName(Long userId) {
        try {
            User user = userService.getById(userId);
            return user == null ? null : user.getRealName();
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
