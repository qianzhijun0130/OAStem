package com.kellen.oa_system.system.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 操作日志查询条件。
 */
@Data
public class OperationLogPageQuery {

    private Integer pageNo = 1;

    private Integer pageSize = 10;

    /**
     * 按操作人精确筛选。
     *
     * <p><b>安全约定</b>：该参数只能在后端算出的可见范围内<b>进一步收窄</b>，
     * 不能突破范围。若传入的用户不在可见集合内，查询结果为空而非报错——
     * 这样既不泄露「该用户是否存在」，也不会让正常操作突然失败。</p>
     */
    private Long userId;
    /** 操作账号，模糊匹配 */
    private String username;

    /** 模块名称，模糊匹配 */
    private String module;

    /** 1成功 0失败 */
    private Integer operationStatus;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
