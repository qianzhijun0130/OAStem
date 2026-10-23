package com.kellen.oa_system.hr.leave.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 地方假别政策，对应表 oa_leave_policy。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_leave_policy")
public class LeavePolicy extends AuditEntity {


    /** 城市 */
    private String city;

    /** 假别：4婚假 5产假 6陪产假 */
    private Integer leaveType;

    /** 法定天数 */
    private java.math.BigDecimal days;

    /** 生效年度 */
    private Integer effectiveYear;

    /** 依据 */
    private String remark;
}
