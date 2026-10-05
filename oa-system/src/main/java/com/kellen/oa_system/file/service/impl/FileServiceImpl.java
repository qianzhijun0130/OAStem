package com.kellen.oa_system.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.file.config.FileStorageProperties;
import com.kellen.oa_system.file.entity.FileRecord;
import com.kellen.oa_system.file.mapper.FileRecordMapper;
import com.kellen.oa_system.file.service.FileService;
import com.kellen.oa_system.file.service.FileStorageService;
import com.kellen.oa_system.file.vo.FileUploadVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件业务实现。
 *
 * <p>相对路径规则：{@code yyyy/MM/{uuid}.{suffix}}。按年月分目录避免单目录堆积过多文件，
 * UUID 命名则规避同名覆盖与「原始文件名含路径/特殊字符」带来的安全问题。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final DateTimeFormatter DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    private final FileStorageService fileStorageService;
    private final FileRecordMapper fileRecordMapper;
    private final FileStorageProperties properties;

    @Override
    public FileUploadVO upload(MultipartFile file) {
        validate(file);

        String originalName = normalizeOriginalName(file.getOriginalFilename());
        String suffix = extractSuffix(originalName);
        String relativePath = buildRelativePath(suffix);

        fileStorageService.store(file, relativePath);
        FileRecord record = saveRecord(file, originalName, suffix, relativePath);

        log.info("文件上传成功 id={} name={} size={} storage={}",
                record.getId(), originalName, record.getFileSize(), fileStorageService.storageType());
        return toVO(record, relativePath);
    }

    @Override
    public DownloadPayload download(Long id) {
        FileRecord record = fileRecordMapper.selectOne(new LambdaQueryWrapper<FileRecord>()
                .eq(FileRecord::getId, id)
                .eq(FileRecord::getIsDeleted, 0));
        if (record == null) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        Resource resource = fileStorageService.load(record.getFilePath());
        increaseDownloadCount(record);
        return new DownloadPayload(record, resource);
    }

    // ---------- 校验 ----------

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_EMPTY);
        }
        long maxBytes = properties.getMaxSizeMb() * BYTES_PER_MB;
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.FILE_SIZE_EXCEEDED,
                    "文件大小不能超过 " + properties.getMaxSizeMb() + "MB");
        }
        String suffix = extractSuffix(normalizeOriginalName(file.getOriginalFilename()));
        if (!properties.getAllowedSuffixes().contains(suffix)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "不支持的扩展名：." + suffix);
        }
    }

    // ---------- 落库 ----------

    private FileRecord saveRecord(MultipartFile file, String originalName, String suffix, String relativePath) {
        FileRecord record = new FileRecord();
        record.setFileName(originalName);
        record.setFilePath(relativePath);
        record.setFileSize(file.getSize());
        record.setFileType(file.getContentType());
        record.setFileSuffix(suffix);
        record.setMd5(calcMd5(file));
        record.setFolderId(0L);
        record.setUploaderId(SecurityContextUtil.getCurrentUserId());
        record.setDownloadCount(0);
        fileRecordMapper.insert(record);
        return record;
    }

    /** 下载计数失败不影响下载本身，因此吞掉异常只告警 */
    private void increaseDownloadCount(FileRecord record) {
        try {
            FileRecord update = new FileRecord();
            update.setId(record.getId());
            update.setDownloadCount((record.getDownloadCount() == null ? 0 : record.getDownloadCount()) + 1);
            fileRecordMapper.updateById(update);
        } catch (Exception e) {
            log.warn("累加下载次数失败 id={}：{}", record.getId(), e.getMessage());
        }
    }

    // ---------- 工具 ----------

    /**
     * 只取文件名部分，剥掉客户端可能带上的路径。
     *
     * <p>原始文件名由客户端完全控制，可能形如 {@code ..\..\web.xml}，直接落库会把
     * 危险内容带进日志与下载头，因此先 cleanPath 再截断长度。</p>
     */
    private String normalizeOriginalName(String originalName) {
        String cleaned = StringUtils.cleanPath(originalName == null ? "" : originalName);
        cleaned = cleaned.replace("\\", "/");
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        if (cleaned.isBlank()) {
            cleaned = "unnamed";
        }
        return cleaned.length() > MAX_ORIGINAL_NAME_LENGTH
                ? cleaned.substring(0, MAX_ORIGINAL_NAME_LENGTH)
                : cleaned;
    }

    private String extractSuffix(String fileName) {
        String suffix = StringUtils.getFilenameExtension(fileName);
        return suffix == null ? "" : suffix.toLowerCase(Locale.ROOT);
    }

    private String buildRelativePath(String suffix) {
        String directory = LocalDate.now().format(DIR_FORMATTER);
        String randomName = UUID.randomUUID().toString().replace("-", "");
        return suffix.isEmpty()
                ? directory + "/" + randomName
                : directory + "/" + randomName + "." + suffix;
    }

    /** 失败时返回 null：MD5 只用于去重辅助，缺了不影响主流程 */
    private String calcMd5(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return DigestUtils.md5DigestAsHex(in);
        } catch (IOException e) {
            log.warn("计算文件 MD5 失败：{}", e.getMessage());
            return null;
        }
    }

    private FileUploadVO toVO(FileRecord record, String relativePath) {
        FileUploadVO vo = new FileUploadVO();
        vo.setId(record.getId());
        vo.setFileName(record.getFileName());
        vo.setUrl(properties.getAccessPrefix() + "/" + relativePath);
        vo.setFileSize(record.getFileSize());
        vo.setFileType(record.getFileType());
        return vo;
    }
}
