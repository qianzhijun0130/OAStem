package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 菜单 / 按钮权限实体，对应表 oa_menu。
 *
 * <p>该表无 is_deleted 字段（菜单用 status 控制启停），
 * 因此继承 {@link BaseEntity} 而非 AuditEntity，并单独声明审计字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_menu")
public class Menu extends BaseEntity {

    private String menuName;

    /** 权限标识，如 system:user:add，用于后端 @PreAuthorize 与前端按钮控制 */
    private String menuCode;

    private Long parentId;

    /** 类型：1目录 2菜单 3按钮 */
    private Integer menuType;

    private String path;

    private String component;

    private String icon;

    private Integer sort;

    /** 是否在侧边栏显示 */
    private Integer visible;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
