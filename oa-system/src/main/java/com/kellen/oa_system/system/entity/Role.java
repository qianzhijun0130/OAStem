package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体，对应表 oa_role。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_role")
public class Role extends AuditEntity {

    private String roleName;

    private String roleCode;

    /** 角色类型：1系统角色 2业务角色 */
    private Integer roleType;

    /** 数据权限范围：1全部 2本部门 3仅本人 */
    private Integer dataScope;

    private String description;

    private Integer sort;

    /** 状态：0禁用 1启用 */
    private Integer status;
}
