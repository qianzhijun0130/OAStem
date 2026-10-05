package com.kellen.oa_system.file.controller;

import com.kellen.oa_system.common.Result;
import com.kellen.oa_system.file.entity.FileRecord;
import com.kellen.oa_system.file.service.FileService;
import com.kellen.oa_system.file.vo.FileUploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 文件上传下载接口。
 *
 * <p>两种访问方式并存：</p>
 * <ul>
 *   <li><b>静态映射</b>：上传返回的 {@code url}（{@code /uploads/**}）可直接给 {@code <img>} 用，
 *       适合头像这类公开图片；</li>
 *   <li><b>鉴权下载</b>：{@code GET /file/download/{id}} 校验登录态，适合报销票据等敏感附件。</li>
 * </ul>
 *
 * <p><b>为什么不挂按钮权限码</b>：换头像、传附件是所有角色都会用到的基础能力，
 * 若为每个新角色都要求额外授权 {@code file:upload}，权限配置会变成负担。
 * 这里只要求登录态；至于「某份附件你能不能看」属于业务级判断，
 * 应由对应业务模块（如审批单）按单据归属校验，而不是在通用文件模块里一刀切。</p>
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.upload(file));
    }

    /**
     * 下载附件。
     *
     * <p>这是本项目唯一不返回 {@code Result} 包装的接口——响应体是文件二进制流，
     * 不能再套一层 JSON。错误场景（文件不存在）仍由全局异常处理返回标准 JSON。</p>
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FileService.DownloadPayload payload = fileService.download(id);
        FileRecord record = payload.record();

        HttpHeaders headers = new HttpHeaders();
        // 用 ContentDisposition 而非手拼字符串：它会按 RFC 5987 处理中文文件名，
        // 手拼 "attachment; filename=中文.pdf" 在多数浏览器下会乱码
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(record.getFileName(), StandardCharsets.UTF_8)
                .build());
        headers.setContentType(resolveMediaType(record.getFileType()));

        return ResponseEntity.ok()
                .headers(headers)
                .body(payload.resource());
    }

    /** 库里存的是上传时客户端声明的 MIME，可能为空或不可信，这里做兜底 */
    private MediaType resolveMediaType(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(fileType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
