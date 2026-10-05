package com.kellen.oa_system.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色下拉选项（用户表单、权限分配等场景复用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleOptionVO {

    private Long id;

    private String roleName;

    private String roleCode;
}
