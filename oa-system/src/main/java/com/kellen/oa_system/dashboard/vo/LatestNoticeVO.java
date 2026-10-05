package com.kellen.oa_system.dashboard.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作台「最新公告」条目。
 */
@Data
public class LatestNoticeVO {

    private Long id;

    private String noticeTitle;

    private Integer noticeType;

    private Integer noticeLevel;

    /** 1置顶 0普通 */
    private Integer topFlag;

    private LocalDateTime publishTime;

    /** 发布人姓名，可能为空（发布人被删除时） */
    private String publisherName;
}
