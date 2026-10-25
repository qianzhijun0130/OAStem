package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import com.kellen.oa_system.security.crypto.EncryptedStringTypeHandler;
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
@TableName(value = "oa_user", autoResultMap = true)
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

    /** 状态：0禁用 1启用 2锁定（账号状态，与任职状态无关） */
    private Integer status;

    /**
     * 任职状态：1待入职 2试用 3正式 4待离职 5已离职 6退休。
     *
     * <p>与 {@link #status} 是两个维度：status 决定「能不能登录」，本字段描述「劳动关系」。
     * 管理员因违规禁用账号时不得改写本字段；反之本字段置为已离职时才联动禁用账号。
     * 「在职」口径统一为 {@code employment_status IN (2,3,4)}，不要用 status=1。</p>
     */
    private Integer employmentStatus;

    /** 岗位ID（oa_position）；{@link #position} 保留为冗余显示名，写入时同步 */
    private Long positionId;

    /** 直属上级用户ID，审批人策略「申请人部门主管」优先取此值 */
    private Long directLeaderId;

    /** 首次参加工作时间，年假按累计工龄计算时使用 */
    private LocalDate workStartDate;

    /** 试用期截止日，用于转正提醒 */
    private LocalDate probationEndDate;

    /**
     * 身份证号（库中 AES-GCM 密文，对外脱敏展示）。
     *
     * <p>typeHandler 做字段级透明加解密；{@code autoResultMap = true} 是前提，
     * 少了它查询结果的解密不生效，会把 {@code ENC:} 密文直接返回给前端。</p>
     */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String idCard;

    private LocalDate birthDate;

    /** 婚姻状况：1未婚 2已婚 3离异 4丧偶 */
    private Integer maritalStatus;

    private String nativePlace;

    private String address;

    private String emergencyName;

    private String emergencyPhone;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;
}
