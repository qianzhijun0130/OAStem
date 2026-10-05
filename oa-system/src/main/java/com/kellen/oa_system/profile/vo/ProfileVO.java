package com.kellen.oa_system.profile.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人中心视图对象。
 *
 * <p>字段分两类：{@code editable} 之外的信息（姓名、工号、部门、岗位、入职时间）属于人事档案，
 * 由管理员在员工管理中维护，个人中心只读展示。</p>
 */
@Data
public class ProfileVO {

    private Long id;

    private String username;

    /** 真实姓名，只读 */
    private String realName;

    private String nickname;

    private String avatar;

    /** 性别：0未知 1男 2女 */
    private Integer gender;

    private String mobile;

    private String email;

    /** 工号，只读 */
    private String workNumber;

    /** 岗位，只读 */
    private String position;

    /** 入职时间，只读 */
    private LocalDate entryDate;

    private Long deptId;

    /** 部门名称，只读 */
    private String deptName;

    /** 角色编码列表，只读 */
    private List<String> roles;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;
}
