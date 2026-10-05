package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作日志（表 oa_operation_log）。
 *
 * <p>该表只写不改、不做逻辑删除——日志本身就是审计证据，删除操作应走
 * 「按时间/条件清理」的显式入口，而不是留一条 is_deleted 标记。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_operation_log")
public class OperationLog extends BaseEntity {

    private Long userId;

    private String username;

    /** 所属模块，如「公告管理」 */
    private String module;

    /** 操作描述，如「发布公告」 */
    private String operation;

    /** 目标方法全限定名 */
    private String method;

    /** 请求参数（JSON，敏感值已脱敏） */
    private String requestParams;

    /** 响应结果（JSON，默认不记录） */
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
