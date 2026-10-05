package com.kellen.oa_system.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门实体，对应表 oa_department。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_department")
public class Department extends AuditEntity {

    private String deptName;

    private String deptCode;

    /**
     * 工号前缀（部门名称汉字拼音首字母，全大写）。
     *
     * <p>为空时由 {@code WorkNumberServiceImpl} 按部门名称自动推导并回写；
     * 管理员可在部门管理中手工覆盖，用于纠正多音字或缩写不合预期的场景。</p>
     */
    private String workNoPrefix;

    /**
     * 该部门已发出的工号流水号当前值，只增不减。
     *
     * <p>员工离职 / 删除后不回退，以此保证工号「不回收、不复用」。</p>
     */
    private Integer workNoSeq;

    private Long companyId;

    private Long parentId;

    private Long leaderId;

    private Integer deptType;

    private Integer sort;

    private Integer status;
}
