package com.kellen.oa_system.notice.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告详情（管理端 / 编辑回显）。
 *
 * <p>范围字段对外用 ID 数组而非逗号串：前端 el-select 多选需要数组，
 * 让转换发生在服务端，避免每个调用方各写一遍 split。</p>
 */
@Data
public class NoticeDetailVO {

    private Long id;

    private String noticeTitle;

    private Integer noticeType;

    private Integer noticeLevel;

    private Integer noticeStatus;

    private Integer topFlag;

    private Integer publishScope;

    private String content;

    private List<Long> scopeDeptIds;

    private List<Long> scopeUserIds;

    private List<Long> attachmentIds;

    /** 附件明细，供详情页直接渲染下载链接 */
    private List<NoticeAttachmentVO> attachments;

    private LocalDateTime publishTime;

    private Long publisherId;

    private String publisherName;

    private Integer viewCount;

    private Long readCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
