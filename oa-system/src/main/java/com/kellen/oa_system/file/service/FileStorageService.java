package com.kellen.oa_system.file.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象层。
 *
 * <p>业务代码只依赖本接口，不关心文件实际存在本地磁盘、服务器磁盘还是对象存储。
 * 新增存储方式（OSS / MinIO / 腾讯云 COS）时只需再写一个实现类并用
 * {@code @ConditionalOnProperty} 绑定 {@code file.storage-type}，业务层零改动。</p>
 *
 * <p><b>约定</b>：入参和返回值一律使用<b>相对路径</b>（如 {@code 2026/09/3f2a....png}），
 * 绝对路径只在实现类内部拼接。这样数据库里的历史数据在换环境后依然有效。</p>
 */
public interface FileStorageService {

    /**
     * 写入文件。
     *
     * @param file         上传的文件
     * @param relativePath 相对路径，由业务层生成（按年月分目录 + UUID 文件名）
     */
    void store(MultipartFile file, String relativePath);

    /**
     * 读取文件。
     *
     * @param relativePath 相对路径
     * @return 可直接用于响应输出的资源句柄
     */
    Resource load(String relativePath);

    /**
     * 删除文件。文件不存在时返回 false，不抛异常——删除失败的清理动作不应打断主流程。
     */
    boolean delete(String relativePath);

    /** 当前生效的存储类型，用于日志与排查 */
    String storageType();
}
