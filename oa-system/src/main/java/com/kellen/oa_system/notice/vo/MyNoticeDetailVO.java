package com.kellen.oa_system.notice.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 公告详情（用户端）。
 *
 * <p>在列表行基础上补正文与附件明细；范围字段不下发——读者只需知道
 * 「这条公告我能看到」，不需要知道它原本发给谁。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyNoticeDetailVO extends MyNoticeVO {

    private String content;

    private Integer viewCount;

    private List<NoticeAttachmentVO> attachments;
}
