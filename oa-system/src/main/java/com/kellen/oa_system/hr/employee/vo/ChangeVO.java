package com.kellen.oa_system.hr.employee.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工异动记录视图。
 */
@Data
public class ChangeVO {

    private Long id;

    private Integer changeType;

    private String changeTypeLabel;

    private LocalDate effectiveDate;

    private String beforeJson;

    private String afterJson;

    private Long instanceId;

    private String remark;

    private String operatorName;

    private LocalDateTime createTime;
}
