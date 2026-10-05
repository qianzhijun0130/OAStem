package com.kellen.oa_system.system.dto;

import lombok.Data;

/**
 * 员工（用户）分页查询入参。
 */
@Data
public class UserPageQuery {

    /** 页码，从 1 开始 */
    private Integer pageNo = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 部门ID（含其全部下级部门） */
    private Long deptId;

    /** 角色ID */
    private Long roleId;

    /** 状态：0禁用 1启用 2锁定 */
    private Integer status;

    /** 关键词：匹配登录账号 / 姓名 / 工号 */
    private String keyword;
}
