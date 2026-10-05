package com.kellen.oa_system.notice.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.notice.dto.NoticePageQuery;
import com.kellen.oa_system.notice.dto.NoticeSaveRequest;
import com.kellen.oa_system.notice.service.NoticeService;
import com.kellen.oa_system.notice.vo.NoticeDetailVO;
import com.kellen.oa_system.notice.vo.NoticeVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

/**
 * 公告管理接口（发布者视角）。
 *
 * <p>权限码与 oa_menu 中 menu_type=3 的按钮权限保持一致。</p>
 */
@RestController
@RequestMapping("/notice")
@RequiredArgsConstructor
@Validated
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:notice:query')")
    public Result<PageResult<NoticeVO>> page(NoticePageQuery query) {
        return Result.success(noticeService.pageNotices(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:query')")
    public Result<NoticeDetailVO> detail(@PathVariable Long id) {
        return Result.success(noticeService.getNoticeDetail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:notice:add')")
    @OperationLog(module = "公告管理", operation = "保存公告")
    public Result<Long> save(@Valid @RequestBody NoticeSaveRequest request) {
        return Result.success(noticeService.saveNotice(request));
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('system:notice:publish')")
    @OperationLog(module = "公告管理", operation = "发布公告 #{#id}")
    public Result<Void> publish(@PathVariable Long id) {
        noticeService.publish(id);
        return Result.success();
    }

    @PutMapping("/{id}/offline")
    @PreAuthorize("hasAuthority('system:notice:publish')")
    @OperationLog(module = "公告管理", operation = "下架公告 #{#id}")
    public Result<Void> offline(@PathVariable Long id) {
        noticeService.offline(id);
        return Result.success();
    }

    @PutMapping("/{id}/top")
    @PreAuthorize("hasAuthority('system:notice:edit')")
    @OperationLog(module = "公告管理", operation = "调整公告置顶 #{#id}")
    public Result<Void> updateTop(@PathVariable Long id,
                                  @RequestParam
                                  @Min(value = 0, message = "是否置顶只能是 0否 1是")
                                  @Max(value = 1, message = "是否置顶只能是 0否 1是") Integer topFlag) {
        noticeService.updateTopFlag(id, topFlag);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:delete')")
    @OperationLog(module = "公告管理", operation = "删除公告 #{#id}")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return Result.success();
    }
}
