package com.kellen.oa_system.system.controller;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.system.dto.UserPageQuery;
import com.kellen.oa_system.system.dto.UserSaveRequest;
import com.kellen.oa_system.system.service.UserImportService;
import com.kellen.oa_system.system.service.UserService;
import com.kellen.oa_system.system.vo.UserImportResultVO;
import com.kellen.oa_system.system.vo.UserVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 员工（用户）管理接口。
 */
@RestController
@RequestMapping("/system/user")
@Validated
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final UserImportService userImportService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user:query')")
    public Result<PageResult<UserVO>> page(UserPageQuery query) {
        return Result.success(userService.pageUsers(query));
    }

    /**
     * 候选用户列表（启用态），供审批转办 / 审批人 / 通知接收人选择。
     *
     * <p>刻意不要求 {@code system:user:query}：该权限仅 HR+超管持有，会导致非 HR 角色
     * 在转办 / 指定接收人时拿不到候选列表（403）。此处仅要求已登录，页面级权限已做管控。</p>
     */
    @GetMapping("/candidates")
    @PreAuthorize("isAuthenticated()")
    public Result<List<UserVO>> candidates() {
        return Result.success(userService.listCandidates());
    }

    /**
     * 下载批量导入模板。
     *
     * <p>模板只含表头与「填写说明」工作表，刻意不放示例行——否则管理员
     * 填完直接上传，系统会把示例行当成真实员工一起导入。</p>
     */
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('system:user:import')")
    public void downloadImportTemplate(HttpServletResponse response) {
        ExcelExportUtil.writeBlankTemplate(response, "员工批量导入模板.xlsx", "员工数据",
                userImportService.templateHeaders(), userImportService.templateNotes());
    }

    /**
     * 批量导入员工（BIZ-OPT-09）。
     *
     * <p><b>逐行成功、失败行跳过</b>（不再是整批拒绝）：返回值里的
     * {@code successCount / failCount / errors} 才是成败依据，HTTP 与业务码都是成功。
     * 这一点必须由前端把明细摊开给 HR 看——否则「接口没报错」被当成「全部导入成功」，
     * 漏掉的那几个人要等发工资时才发现。</p>
     *
     * <p>旧版 10 列模板会在表头校验阶段被拒，提示「请下载新模板」。</p>
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('system:user:import')")
    @OperationLog(module = "用户管理", operation = "批量导入员工", saveParams = false)
    public Result<UserImportResultVO> importUsers(@RequestParam("file") MultipartFile file) {
        return Result.success(userImportService.importUsers(file));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('system:user:add', 'system:user:edit')")
    @OperationLog(module = "用户管理", operation = "保存用户")
    public Result<Long> save(@Valid @RequestBody UserSaveRequest request) {
        return Result.success(userService.saveUser(request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @OperationLog(module = "用户管理", operation = "启用或禁用用户 #{#id}")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam
                                     @Min(value = 0, message = "状态只能是 0禁用 1启用 2锁定")
                                     @Max(value = 2, message = "状态只能是 0禁用 1启用 2锁定") Integer status) {
        userService.updateUserStatus(id, status);
        return Result.success();
    }

    @PutMapping("/{id}/reset-pwd")
    @PreAuthorize("hasAuthority('system:user:resetPwd')")
    @OperationLog(module = "用户管理", operation = "重置用户密码 #{#id}", saveParams = false)
    public Result<Void> resetPwd(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @OperationLog(module = "用户管理", operation = "删除用户 #{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }
}
