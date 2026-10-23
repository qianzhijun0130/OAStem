package com.kellen.oa_system.hr.position.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 岗位编制，对应表 oa_position_headcount。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_position_headcount")
public class PositionHeadcount extends AuditEntity {

    /** 部门ID */
    private Long deptId;

    /** 岗位ID */
    private Long positionId;

    /** 年度 */
    private Integer year;

    /** 编制数 */
    private Integer headcount;

    /** 调整说明 */
    private String remark;
}
