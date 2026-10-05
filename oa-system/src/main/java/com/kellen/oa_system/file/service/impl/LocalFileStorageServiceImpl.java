package com.kellen.oa_system.file.service.impl;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.file.config.FileStorageProperties;
import com.kellen.oa_system.file.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地磁盘存储实现。
 *
 * <p>激活条件：{@code file.storage-type=local}（未配置时默认激活，保证开箱即用）。
 * 换云存储时新增实现类并把该配置切成 {@code oss}/{@code minio} 即可，本类自动让位。</p>
 *
 * <p><b>安全要点</b>：所有路径都要经 {@link #resolve(String)} 归一化并校验仍在根目录内，
 * 否则 {@code ../../} 形式的相对路径可以读写存储根目录之外的文件（路径穿越）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageServiceImpl implements FileStorageService {

    /** 路径穿越的典型特征，命中即拒绝，不必等归一化后再判断 */
    private static final String PARENT_DIR = "..";

    private final FileStorageProperties properties;

    @Override
    public void store(MultipartFile file, String relativePath) {
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            log.error("文件写入失败 relativePath={}", relativePath, e);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public Resource load(String relativePath) {
        Path target = resolve(relativePath);
        if (!Files.exists(target)) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        return new FileSystemResource(target);
    }

    @Override
    public boolean delete(String relativePath) {
        try {
            return Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | BizException e) {
            // 清理失败只告警：调用方通常已在做删除业务记录，不该因文件残留而回滚
            log.warn("文件删除失败 relativePath={}：{}", relativePath, e.getMessage());
            return false;
        }
    }

    @Override
    public String storageType() {
        return properties.getStorageType();
    }

    /**
     * 把相对路径解析为根目录下的绝对路径，并确保结果没有跑出根目录。
     *
     * @throws BizException 相对路径为空、含 {@code ..} 或归一化后越界
     */
    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.contains(PARENT_DIR)) {
            throw new BizException(ErrorCode.FILE_PATH_INVALID);
        }
        Path root = rootPath();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.FILE_PATH_INVALID);
        }
        return target;
    }

    /** 存储根目录（绝对化 + 归一化），首次使用时创建 */
    private Path rootPath() {
        String basePath = properties.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "未配置文件存储根目录 file.base-path");
        }
        Path root = Paths.get(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件存储目录不可用：" + basePath);
        }
        return root;
    }

    /** 目录不可用时启动即暴露问题，好过等到用户上传时才报错 */
    @PostConstruct
    void logStorageRoot() {
        log.info("本地文件存储已启用，根目录：{}", rootPath());
    }
}
