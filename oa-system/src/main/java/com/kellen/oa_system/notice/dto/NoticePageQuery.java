package com.kellen.oa_system.notice.dto;

import lombok.Data;

/**
 * 公告管理端分页查询条件。
 *
 * <p>所有条件均可为空，为空即不参与过滤。</p>
 */
@Data
public class NoticePageQuery {

    private Integer pageNo = 1;

    private Integer pageSize = 10;

    /** 标题模糊匹配 */
    private String noticeTitle;

    /** 1通知 2公告 3新闻 */
    private Integer noticeType;

    /** 1普通 2重要 3紧急 */
    private Integer noticeLevel;

    /** 0草稿 1已发布 2已下架 */
    private Integer noticeStatus;
}
