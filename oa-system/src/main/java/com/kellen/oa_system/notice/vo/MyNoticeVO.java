package com.kellen.oa_system.notice.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告列表行（用户端「我的公告」）。
 */
@Data
public class MyNoticeVO {

    private Long id;

    private String noticeTitle;

    private Integer noticeType;

    private Integer noticeLevel;

    private Integer topFlag;

    private LocalDateTime publishTime;

    private Long publisherId;

    private String publisherName;

    /** 0未读 1已读 */
    private Integer readFlag;

    /** 0无附件 1有附件 */
    private Integer hasAttachment;
}
