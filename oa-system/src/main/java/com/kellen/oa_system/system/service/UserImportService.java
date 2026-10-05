package com.kellen.oa_system.system.service;

import com.kellen.oa_system.system.vo.UserImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 员工批量导入服务。
 */
public interface UserImportService {

    /**
     * 解析 Excel 并批量新增员工。
     *
     * <p>采用「整批校验、全对才入库」：任一行校验失败则整体不入库，
     * 通过返回值的 {@code errors} 给出逐行原因。</p>
     *
     * @param file 上传的 Excel（xlsx / xls）
     * @return 导入结果
     */
    UserImportResultVO importUsers(MultipartFile file);

    /**
     * 导入模板的表头（顺序即列序号）。
     *
     * <p>下载与解析共用同一份定义，避免「模板改了解析没改」导致整批报格式错误。</p>
     */
    List<String> templateHeaders();

    /** 导入模板「填写说明」工作表的文本，每行一条 */
    List<String> templateNotes();
}
