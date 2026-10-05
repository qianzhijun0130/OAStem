package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.system.dto.LoginLogPageQuery;
import com.kellen.oa_system.system.service.LoginLogService;
import com.kellen.oa_system.system.vo.LoginLogVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 登录日志查询接口。
 */
@RestController
@RequestMapping("/system/login-log")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    /**
     * 分页查询登录日志。
     *
     * <p><b>鉴权说明（重要）</b>：本端点<b>只需登录</b>，不要求
     * {@code system:login-log:query}。因为「查看自己的日志」是个人权限而非管理权限 ——
     * 若沿用管理权限码，普通员工将无法查看自己的操作记录。</p>
     *
     * <p>可见范围由服务端 {@code LogScopeResolver} 强制注入：
     * 超管不限、部门负责人限本部门及下属部门、其余仅本人。
     * 前端传入的 {@code userId} 只能在范围内收窄，越权时返回空集而非报错。</p>
     */
    @GetMapping("/page")
    public Result<PageResult<LoginLogVO>> page(LoginLogPageQuery query) {
        return Result.success(loginLogService.page(query));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('system:login-log:export')")
    @OperationLog(module = "日志管理", operation = "导出登录日志")
    public void export(LoginLogPageQuery query, HttpServletResponse response) {
        loginLogService.export(response, query);
    }

    /**
     * 清理登录日志。
     *
     * <p><b>三条安全约束</b>：</p>
     * <ol>
     *   <li><b>仅超级管理员</b>：{@code hasRole('SUPER_ADMIN')}。日志可被清理，
     *       意味着追溯链可以被抹除，因此不能下放给任何业务角色；</li>
     *   <li><b>必须留痕</b>：{@code @OperationLog} 写入的审计日志，其时间戳晚于本方法
     *       采用的清理分界，故<b>不会被这次清理自己删掉</b>；</li>
     *   <li><b>按时间清理有保留期护栏</b>：见 {@code resolveCleanCutoff}。</li>
     * </ol>
     *
     * @param before 清理截止时间（不含），格式 {@code yyyy-MM-dd HH:mm:ss}；{@code all=true} 时忽略
     * @param all    是否全量清理。true = 清除全部历史日志（仍保留本次操作的审计日志）
     * @return 实际删除行数
     */
    @DeleteMapping("/clean")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(module = "日志管理", operation = "清理登录日志 #{#all ? '全量' : #before}")
    public Result<Integer> clean(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime before,
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        return Result.success(loginLogService.clean(before, all));
    }
}
