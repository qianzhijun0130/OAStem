package com.kellen.oa_system.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色新增 / 修改入参。
 *
 * <p>id 为空表示新增，非空表示修改。</p>
 */
@Data
public class RoleSaveRequest {

    /** 角色ID；新增时为空 */
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称长度不能超过 50")
    private String roleName;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "角色编码只能包含字母、数字与下划线")
    private String roleCode;

    /** 角色类型：1系统角色 2业务角色 */
    @Min(value = 1, message = "角色类型只能是 1系统角色 2业务角色")
    @Max(value = 2, message = "角色类型只能是 1系统角色 2业务角色")
    private Integer roleType;

    /** 数据权限范围：1全部 2本部门 3仅本人 */
    @Min(value = 1, message = "数据范围只能是 1全部 2本部门 3仅本人")
    @Max(value = 3, message = "数据范围只能是 1全部 2本部门 3仅本人")
    private Integer dataScope;

    @Size(max = 200, message = "角色描述长度不能超过 200")
    private String description;

    /** 状态：0禁用 1启用 */
    @Min(value = 0, message = "状态只能是 0禁用 1启用")
    @Max(value = 1, message = "状态只能是 0禁用 1启用")
    private Integer status;

    private Integer sort;
}
