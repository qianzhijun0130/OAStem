package com.kellen.oa_system.notice.vo;

import lombok.Data;

/**
 * 附件摘要，供公告详情页渲染下载入口。
 *
 * <p>只回传展示与下载所需的最小字段，不暴露物理存储路径（filePath）。</p>
 */
@Data
public class NoticeAttachmentVO {

    private Long id;

    private String fileName;

    /** 下载地址（/file/download/{id}） */
    private String url;

    private Long fileSize;
}
