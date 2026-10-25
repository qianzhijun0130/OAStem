package com.kellen.oa_system.hr.leave.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 地方假别政策。
 */
@Data
public class LeavePolicyVO {

    private Long id;

    /** 城市；「默认」表示未单独配置城市的兜底口径 */
    private String city;

    /** 假别：4婚假 5产假 6陪产假 7丧假 */
    private Integer leaveType;

    private String leaveTypeLabel;

    /** 法定天数 */
    private BigDecimal days;

    private Integer effectiveYear;

    /** 依据（地方条例名称/文号，便于 HR 追溯） */
    private String remark;

    private LocalDateTime updateTime;
}
