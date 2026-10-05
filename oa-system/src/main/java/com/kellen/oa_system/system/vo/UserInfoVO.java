package com.kellen.oa_system.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息（供前端初始化用户态、动态菜单与按钮权限）。
 */
@Data
@Builder
public class UserInfoVO {

    private Long userId;

    private String username;

    private String realName;

    private String nickname;

    private String avatar;

    private Long deptId;

    private String deptName;

    private Long companyId;

    private String position;

    private String workNumber;

    private String mobile;

    private String email;

    /** 角色编码，如 SUPER_ADMIN */
    private List<String> roles;

    /** 权限码，如 system:user:add */
    private List<String> permissions;
}
