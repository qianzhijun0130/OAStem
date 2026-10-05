package com.kellen.oa_system.notice.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.notice.dto.MyNoticePageQuery;
import com.kellen.oa_system.notice.dto.NoticePageQuery;
import com.kellen.oa_system.notice.dto.NoticeSaveRequest;
import com.kellen.oa_system.notice.vo.MyNoticeDetailVO;
import com.kellen.oa_system.notice.vo.MyNoticeVO;
import com.kellen.oa_system.notice.vo.NoticeDetailVO;
import com.kellen.oa_system.notice.vo.NoticeVO;

import java.util.List;

/**
 * 公告通知服务。
 *
 * <p>分两组能力：管理端（发布者视角，全状态可见）与用户端（读者视角，只可见
 * 落在自己发布范围内的公告）。用户端的所有方法都以当前登录用户为唯一主体，
 * 接口不接受 userId / deptId 参数。</p>
 */
public interface NoticeService {

    // ---------- 管理端 ----------

    /** 管理端分页（含草稿、已下架） */
    PageResult<NoticeVO> pageNotices(NoticePageQuery query);

    /** 公告详情，用于编辑回显 */
    NoticeDetailVO getNoticeDetail(Long id);

    /** 新增或编辑公告（仅草稿可编辑），返回公告 ID */
    Long saveNotice(NoticeSaveRequest request);

    /** 发布：草稿/已下架 → 已发布，并刷新发布时间 */
    void publish(Long id);

    /** 下架：已发布 → 已下架 */
    void offline(Long id);

    /** 置顶 / 取消置顶 */
    void updateTopFlag(Long id, Integer topFlag);

    /** 删除公告（连同其阅读记录） */
    void deleteNotice(Long id);

    // ---------- 用户端 ----------

    /** 我的公告分页：按当前用户的可见范围过滤 */
    PageResult<MyNoticeVO> pageMyNotices(MyNoticePageQuery query);

    /** 我的公告详情，首次打开自动标记已读并累加阅读数 */
    MyNoticeDetailVO getMyNoticeDetail(Long id);

    /** 当前用户可见范围内的未读公告数 */
    long countUnread();

    /** 当前用户可见的最新公告，供工作台使用 */
    List<MyNoticeVO> listLatest(int limit);
}
