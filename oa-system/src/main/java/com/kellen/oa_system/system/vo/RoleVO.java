package com.kellen.oa_system.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色视图对象（列表行）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleVO {

    private Long id;

    private String roleName;

    private String roleCode;

    /** 角色类型：1系统角色 2业务角色 */
    private Integer roleType;

    /** 数据权限范围：1全部 2本部门 3仅本人 */
    private Integer dataScope;

    private String description;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private Integer sort;

    private LocalDateTime createTime;

    /** 拥有该角色的用户数 */
    private Long userCount;
}
