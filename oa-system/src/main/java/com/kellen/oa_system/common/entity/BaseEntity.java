package com.kellen.oa_system.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;

/**
 * 实体最小基类：仅含主键。
 *
 * <p>适用于没有审计字段的中间关联表（如 oa_user_role、oa_role_menu），
 * 需要 create_time / update_time / is_deleted 的实体请继承 {@link AuditEntity}。</p>
 */
@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
}
