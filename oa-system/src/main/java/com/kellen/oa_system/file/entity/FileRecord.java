package com.kellen.oa_system.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件元数据实体，对应表 oa_file。
 *
 * <p><b>file_path 存相对路径</b>（如 {@code 2026/09/3f2a...png}），绝不存绝对路径——
 * 这样本地开发与服务器部署共用同一份数据，换存储位置无需改库。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_file")
public class FileRecord extends AuditEntity {

    /** 上传时的原始文件名，用于下载时还原给用户 */
    private String fileName;

    /** 相对路径（不含存储根目录） */
    private String filePath;

    private Long fileSize;

    /** MIME 类型，如 image/png */
    private String fileType;

    private String fileSuffix;

    private String md5;

    /** 所属目录，暂不使用（保留字段），默认 0 表示根目录 */
    private Long folderId;

    private Long uploaderId;

    private Integer downloadCount;
}
