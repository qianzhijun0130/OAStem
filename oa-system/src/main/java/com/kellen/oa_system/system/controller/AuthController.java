package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.util.IpUtil;
import com.kellen.oa_system.security.JwtUtil;
import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.security.OnlineUserRegistry;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.security.TokenBlacklistService;
import com.kellen.oa_system.system.dto.LoginRequest;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.service.AuthService;
import com.kellen.oa_system.system.service.MenuService;
import com.kellen.oa_system.system.service.UserService;
import com.kellen.oa_system.system.vo.LoginResponse;
import com.kellen.oa_system.system.vo.MenuVO;
import com.kellen.oa_system.system.vo.UserInfoVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证与用户态接口。
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;
    private final MenuService menuService;
    private final DepartmentMapper departmentMapper;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final OnlineUserRegistry onlineUserRegistry;

    /**
     * 登录。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        String clientIp = IpUtil.getClientIp(httpRequest);
        return Result.success(authService.login(request, clientIp, httpRequest.getHeader(USER_AGENT_HEADER)));
    }

    /**
     * 获取当前登录用户的基本信息、角色与权限码。
     */
    @GetMapping("/info")
    public Result<UserInfoVO> info() {
        LoginUser loginUser = SecurityContextUtil.getCurrentUser();
        User user = userService.getById(loginUser.getUserId());
        if (user == null) {
            return Result.fail(com.kellen.oa_system.common.ErrorCode.USER_NOT_FOUND);
        }
        return Result.success(UserInfoVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .deptId(user.getDeptId())
                .deptName(resolveDeptName(user.getDeptId()))
                .companyId(user.getCompanyId())
                .position(user.getPosition())
                .workNumber(user.getWorkNumber())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .roles(loginUser.getRoles())
                .permissions(loginUser.getPermissions())
                .build());
    }

    /**
     * 获取当前用户的菜单树（前端据此渲染侧边栏）。
     *
     * <p>与 {@link #info()} 同口径：令牌有效期内账号可能已被删除，此处必须校验用户仍然存在
     * （{@code @TableLogic} 会自动排除逻辑删除行），否则已删用户的旧令牌仍能持续拉取菜单。</p>
     */
    @GetMapping("/menus")
    public Result<List<MenuVO>> menus() {
        LoginUser loginUser = SecurityContextUtil.getCurrentUser();
        User user = userService.getById(loginUser.getUserId());
        if (user == null) {
            return Result.fail(com.kellen.oa_system.common.ErrorCode.USER_NOT_FOUND);
        }
        return Result.success(menuService.getMenuTreeByUserId(user.getId()));
    }

    /**
     * 登出。
     *
     * <p>JWT 无状态，服务端不保存会话，所以「登出」必须做两件事：
     * ① 把当前令牌写入 Redis 黑名单（TTL = 令牌剩余寿命），否则退出后旧令牌仍能调接口；
     * ② 清理当前线程的安全上下文。前端仍需丢弃本地令牌。</p>
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = resolveBearerToken(request);
        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                tokenBlacklistService.blacklist(token, jwtUtil.getRemainingSeconds(claims));
                // 同步移除在线会话：否则「已登出」的账号还会挂在在线列表里直到令牌自然过期
                onlineUserRegistry.unregister(jwtUtil.getUserId(claims));
            } catch (JwtException | IllegalArgumentException e) {
                // 令牌本身已过期或伪造：无需登记，它本来就不可用
                log.debug("登出时令牌不可解析，跳过黑名单登记: {}", e.getMessage());
            }
        }
        SecurityContextHolder.clearContext();
        return Result.success();
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private String resolveDeptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        try {
            Department department = departmentMapper.selectById(deptId);
            return department == null ? null : department.getDeptName();
        } catch (Exception e) {
            log.warn("查询部门名称失败, deptId={}", deptId, e);
            return null;
        }
    }
}
