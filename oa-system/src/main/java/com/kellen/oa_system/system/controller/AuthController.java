package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.util.IpUtil;
import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.security.SecurityContextUtil;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private final UserService userService;
    private final MenuService menuService;
    private final DepartmentMapper departmentMapper;

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
     * 登出。JWT 无状态，服务端不保存令牌，这里仅清理当前线程的安全上下文，
     * 前端需自行丢弃本地令牌。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        SecurityContextHolder.clearContext();
        return Result.success();
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
