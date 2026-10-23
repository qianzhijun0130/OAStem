package com.kellen.oa_system.hr.position.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 岗位，对应表 oa_position。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_position")
public class Position extends AuditEntity {

    /** 岗位编码（同公司内唯一） */
    private String positionCode;

    /** 岗位名称 */
    private String positionName;

    /** 所属公司ID */
    private Long companyId;

    /** 所属部门ID */
    private Long deptId;

    /** 序列：M管理 P技术 S职能 O操作 */
    private String series;

    /** 职级，如 P5/M2 */
    private String levelCode;

    /** 编制数，0=未设置 */
    private Integer headcount;

    /** 岗位说明 */
    private String description;

    /** 状态：0停用 1启用 */
    private Integer status;

    /** 排序号 */
    private Integer sort;
}
