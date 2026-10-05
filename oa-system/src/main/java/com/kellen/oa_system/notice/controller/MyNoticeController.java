package com.kellen.oa_system.notice.controller;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.notice.dto.MyNoticePageQuery;
import com.kellen.oa_system.notice.service.NoticeService;
import com.kellen.oa_system.notice.vo.MyNoticeDetailVO;
import com.kellen.oa_system.notice.vo.MyNoticeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的公告接口（读者视角）。
 *
 * <p><b>不挂按钮权限码</b>：读公告是所有登录角色的基础能力，加码会让每个新角色
 * 都要额外授权一次才能看到公告。可见性由服务端按发布范围判定，与权限码无关。</p>
 *
 * <p>路径刻意避开 {@code /notice/{id}}：本控制器全部为 3 段路径（{@code /notice/my/...}），
 * 与管理端的 2 段路径不在同一层级，不存在「my 被当成 id 解析」的歧义。</p>
 */
@RestController
@RequestMapping("/notice/my")
@RequiredArgsConstructor
public class MyNoticeController {

    private final NoticeService noticeService;

    @GetMapping("/page")
    public Result<PageResult<MyNoticeVO>> page(MyNoticePageQuery query) {
        return Result.success(noticeService.pageMyNotices(query));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(noticeService.countUnread());
    }

    @GetMapping("/latest")
    public Result<List<MyNoticeVO>> latest(@RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(noticeService.listLatest(limit));
    }

    @GetMapping("/{id}")
    public Result<MyNoticeDetailVO> detail(@PathVariable Long id) {
        return Result.success(noticeService.getMyNoticeDetail(id));
    }
}
