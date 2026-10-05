package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.system.dto.OperationLogPageQuery;
import com.kellen.oa_system.system.service.OperationLogService;
import com.kellen.oa_system.system.vo.OperationLogVO;
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
 * 操作日志查询接口。
 *
 * <p>日志是审计数据，只有查询/导出/清理三个动作，不接受任何写操作——
 * 日志一旦可被业务侧修改，就不再是证据。</p>
 */
@RestController
@RequestMapping("/system/operation-log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    /**
     * 分页查询操作日志。
     *
     * <p><b>鉴权说明（重要）</b>：本端点<b>只需登录</b>，不要求
     * {@code system:operation-log:query}。因为「查看自己的日志」是个人权限而非管理权限 ——
     * 若沿用管理权限码，普通员工将无法查看自己的操作记录。</p>
     *
     * <p>可见范围由服务端 {@code LogScopeResolver} 强制注入：
     * 超管不限、部门负责人限本部门及下属部门、其余仅本人。
     * 前端传入的 {@code userId} 只能在范围内收窄，越权时返回空集而非报错。</p>
     */
    @GetMapping("/page")
    public Result<PageResult<OperationLogVO>> page(OperationLogPageQuery query) {
        return Result.success(operationLogService.page(query));
    }

    /**
     * 按当前查询条件导出。
     *
     * <p>导出本身也记一条操作日志：日志数据外流属于敏感行为，需要留痕。</p>
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('system:operation-log:export')")
    @OperationLog(module = "日志管理", operation = "导出操作日志")
    public void export(OperationLogPageQuery query, HttpServletResponse response) {
        operationLogService.export(response, query);
    }

    /**
     * 清理操作日志。
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
    @OperationLog(module = "日志管理", operation = "清理操作日志 #{#all ? '全量' : #before}")
    public Result<Integer> clean(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime before,
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        return Result.success(operationLogService.clean(before, all));
    }
}
