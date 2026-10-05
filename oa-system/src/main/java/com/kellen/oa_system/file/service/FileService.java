package com.kellen.oa_system.file.service;

import com.kellen.oa_system.file.entity.FileRecord;
import com.kellen.oa_system.file.vo.FileUploadVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件业务服务：在存储抽象之上补齐「校验 + 元数据登记」的业务语义。
 *
 * <p>职责边界：本层负责<b>能不能传</b>（空文件、后缀白名单、大小上限）与<b>传完怎么记</b>
 * （写 oa_file），具体写到磁盘还是对象存储交给 {@link FileStorageService}。</p>
 */
public interface FileService {

    /**
     * 上传并登记文件。
     *
     * @throws com.kellen.oa_system.common.BizException 文件为空 / 后缀不在白名单 / 超出大小上限
     */
    FileUploadVO upload(MultipartFile file);

    /**
     * 取出文件内容与元数据用于下载，同时累加下载次数。
     *
     * @throws com.kellen.oa_system.common.BizException 记录不存在或物理文件已丢失
     */
    DownloadPayload download(Long id);

    /** 下载载荷：元数据 + 文件资源 */
    record DownloadPayload(FileRecord record, Resource resource) {
    }
}
