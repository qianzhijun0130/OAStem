package com.kellen.oa_system.notice.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告列表行（管理端）。
 *
 * <p>不含 content：正文是 longtext，列表页用不到，带上会让分页查询的传输量翻几十倍。</p>
 */
@Data
public class NoticeVO {

    private Long id;

    private String noticeTitle;

    /** 1通知 2公告 3新闻 */
    private Integer noticeType;

    /** 1普通 2重要 3紧急 */
    private Integer noticeLevel;

    /** 0草稿 1已发布 2已下架 */
    private Integer noticeStatus;

    /** 0否 1是 */
    private Integer topFlag;

    /** 1全部 2指定部门 3指定人员 */
    private Integer publishScope;

    /** 逗号分隔的文件 ID；用于列表判断有无附件 */
    private String attachmentIds;

    private LocalDateTime publishTime;

    private Long publisherId;

    private String publisherName;

    private Integer viewCount;

    /** 已读人数 */
    private Long readCount;

    private LocalDateTime createTime;
}
