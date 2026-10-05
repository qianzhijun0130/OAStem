package com.kellen.oa_system.system.dto;

import lombok.Data;

/**
 * 操作日志采集载体。
 *
 * <p>刻意不复用实体：实体归持久层，切面只负责「收集到哪些事实」，
 * 两者解耦后调整表结构不会波及切面。</p>
 */
@Data
public class OperationLogRecord {

    private Long userId;

    private String username;

    private String module;

    private String operation;

    private String method;

    private String requestParams;

    private String responseResult;

    private String ipAddress;

    private String location;

    private Integer costTime;

    /** 1成功 0失败 */
    private Integer operationStatus;

    private String errorMsg;
}
