package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户（员工）实体，对应表 oa_user。
 *
 * <p>安全约束：本类禁止直接作为接口返回值，对外一律使用 VO，
 * 避免 password 等敏感字段泄漏到响应中。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_user")
public class User extends AuditEntity {

    private String username;

    private String password;

    private String realName;

    private String nickname;

    private String avatar;

    /** 性别：1男 2女 */
    private Integer gender;

    private String mobile;

    private String email;

    private Long companyId;

    private Long deptId;

    private String position;

    private LocalDate entryDate;

    private String workNumber;

    private Integer sort;

    /** 状态：0禁用 1启用 2锁定 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;
}
