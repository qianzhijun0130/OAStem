package com.kellen.oa_system.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜单新增 / 修改入参。
 *
 * <p>id 为空表示新增，非空表示修改。顶级菜单的 parentId 传 0。
 * 菜单类型：1目录 2菜单 3按钮。</p>
 *
 * <p>「类型 ↔ 字段」的条件约束（目录/菜单必填 path、菜单必填 component）无法用字段级注解表达，
 * 由 {@code MenuServiceImpl#assertTypeFieldsValid} 在服务层校验。</p>
 */
@Data
public class MenuSaveRequest {

    /** 菜单ID；新增时为空 */
    private Long id;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称长度不能超过 50")
    private String menuName;

    @NotBlank(message = "权限标识不能为空")
    @Size(max = 100, message = "权限标识长度不能超过 100")
    @Pattern(regexp = "^[A-Za-z0-9_:]+$", message = "权限标识只能包含字母、数字、下划线与冒号")
    private String menuCode;

    @NotNull(message = "菜单类型不能为空")
    @Min(value = 1, message = "菜单类型只能是 1目录 2菜单 3按钮")
    @Max(value = 3, message = "菜单类型只能是 1目录 2菜单 3按钮")
    private Integer menuType;

    /** 路由地址，目录(1)与菜单(2)必填 */
    @Size(max = 200, message = "路由地址长度不能超过 200")
    private String path;

    /** 组件路径，菜单(2)必填，如 system/dept/index */
    @Size(max = 200, message = "组件路径长度不能超过 200")
    private String component;

    @Size(max = 50, message = "图标长度不能超过 50")
    private String icon;

    /** 上级菜单ID，顶级传 0 */
    private Long parentId;

    private Integer sort;

    /** 是否在侧边栏显示：0隐藏 1显示 */
    @Min(value = 0, message = "是否显示只能是 0隐藏 1显示")
    @Max(value = 1, message = "是否显示只能是 0隐藏 1显示")
    private Integer visible;

    /** 状态：0禁用 1启用 */
    @Min(value = 0, message = "状态只能是 0禁用 1启用")
    @Max(value = 1, message = "状态只能是 0禁用 1启用")
    private Integer status;
}
