package com.kellen.oa_system.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志列表行。
 */
@Data
public class OperationLogVO {

    private Long id;

    private Long userId;

    private String username;

    private String module;

    private String operation;

    private String method;

    private String requestParams;

    private String responseResult;

    private String ipAddress;

    private String location;

    /** 耗时（毫秒） */
    private Integer costTime;

    /** 1成功 0失败 */
    private Integer operationStatus;

    private String errorMsg;

    private LocalDateTime createTime;
}
