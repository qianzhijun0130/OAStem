package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户-角色关联实体，对应表 oa_user_role（无逻辑删除字段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_user_role")
public class UserRole extends BaseEntity {

    private Long userId;

    private Long roleId;
}
