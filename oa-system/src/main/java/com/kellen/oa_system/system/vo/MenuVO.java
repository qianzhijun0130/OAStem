package com.kellen.oa_system.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 菜单视图对象（树形结构），供前端渲染侧边栏。
 *
 * <p>children 为空列表而非 null，避免前端递归时反复做空判断。</p>
 */
@Data
@Builder
public class MenuVO {

    private Long id;

    private Long parentId;

    private String menuName;

    private String menuCode;

    /** 1目录 2菜单 3按钮 */
    private Integer menuType;

    private String path;

    private String component;

    private String icon;

    private Integer sort;

    private Integer visible;

    /** 0禁用 1启用 */
    private Integer status;

    @Builder.Default
    private List<MenuVO> children = List.of();
}
