package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色-菜单关联实体，对应表 oa_role_menu（无逻辑删除字段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_role_menu")
public class RoleMenu extends BaseEntity {

    private Long roleId;

    private Long menuId;
}
