package com.kellen.oa_system.system.dto;

import lombok.Data;

/**
 * 角色分页查询入参。
 */
@Data
public class RolePageQuery {

    /** 页码，从 1 开始 */
    private Integer pageNo = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 角色名称模糊匹配 */
    private String roleName;

    /** 状态过滤：0禁用 1启用 */
    private Integer status;
}
