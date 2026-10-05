package com.kellen.oa_system.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件存储配置。
 *
 * <p>设计原则（见《开发文档/文件上传下载问题.md》）：代码不写死任何盘符或绝对路径，
 * 所有与环境相关的内容都收敛到这里。本地开发与服务器部署只改 {@code file.base-path}，
 * 换成云存储只改 {@code file.storage-type} 并新增一个 {@code FileStorageService} 实现类。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {

    /** 存储实现类型：local（本地磁盘）/ oss / minio，由实现类的 @ConditionalOnProperty 决定装配哪个 */
    private String storageType = "local";

    /**
     * 存储根目录。本地开发形如 {@code F:/OAStem/storage/files}，服务器部署形如 {@code /opt/oa/storage}。
     * 数据库里只存相对路径，因此换环境时历史数据无需迁移或改写。
     */
    private String basePath;

    /** 静态资源访问前缀，用于把存储目录挂到 HTTP 路径上（头像等公开图片走这里） */
    private String accessPrefix = "/uploads";

    /** 单文件大小上限（MB），与 spring.servlet.multipart.max-file-size 保持一致 */
    private long maxSizeMb = 10;

    /** 允许上传的后缀（小写、不带点）。白名单而非黑名单，避免绕过（如 .jsp、.sh） */
    private List<String> allowedSuffixes = List.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv",
            "zip", "rar", "7z");
}
