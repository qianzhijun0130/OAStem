package com.kellen.oa_system.system.online.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.system.online.service.OnlineUserService;
import com.kellen.oa_system.system.online.vo.OnlineUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线用户（管理端）。
 *
 * <p>列表与踢出都挂按钮权限码：能看到「谁在线」本身就是敏感能力，
 * 不应默认对所有登录用户开放。权限码与菜单 `system:online:*` 一一对应。</p>
 */
@RestController
@RequestMapping("/system/online")
@RequiredArgsConstructor
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:online:list')")
    public Result<PageResult<OnlineUserVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(onlineUserService.page(keyword, pageNo, pageSize));
    }

    /**
     * 强制下线：让该用户「此刻之前签发」的全部令牌立即失效。
     *
     * <p>用 POST 而不是 DELETE：这不是删除资源，而是「施加一个状态变更」，
     * 且需要写入操作日志（DELETE 容易被网关/WAF 当成幂等只读操作处理）。</p>
     */
    @PostMapping("/{userId}/kick")
    @PreAuthorize("hasAuthority('system:online:kick')")
    @OperationLog(module = "在线用户", operation = "强制下线")
    public Result<Void> kick(@PathVariable Long userId) {
        onlineUserService.kick(userId);
        return Result.success();
    }
}
