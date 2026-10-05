package com.kellen.oa_system.file.vo;

import lombok.Data;

/**
 * 上传结果。前端拿到 url 即可直接用于 &lt;img&gt; / 附件链接。
 */
@Data
public class FileUploadVO {

    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 可直接访问的相对地址，如 /uploads/2026/09/xxx.png */
    private String url;

    private Long fileSize;

    private String fileType;
}
