package com.kellen.oa_system.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增 / 修改入参。
 *
 * <p>id 为空表示新增，非空表示修改。顶级部门的 parentId 传 0。</p>
 */
@Data
public class DeptSaveRequest {

    /** 部门ID；新增时为空 */
    private Long id;

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 100, message = "部门名称长度不能超过 100")
    private String deptName;

    @NotBlank(message = "部门编码不能为空")
    @Size(max = 50, message = "部门编码长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "部门编码只能包含字母、数字、下划线与中划线")
    private String deptCode;

    /**
     * 工号前缀（全大写字母数字）。留空表示由系统按部门名称拼音首字母自动生成；
     * 新增后再次保存时留空则保留原值，不会被自动生成的规则覆盖。
     */
    @Size(max = 16, message = "工号前缀长度不能超过 16")
    @Pattern(regexp = "^[A-Za-z0-9]*$", message = "工号前缀只能包含字母与数字")
    private String workNoPrefix;

    /**
     * 所属公司ID。顶级部门必填；下级部门留空时自动继承上级部门的公司。
     */
    private Long companyId;

    /** 上级部门ID，顶级部门传 0 */
    private Long parentId;

    /** 部门负责人（用户ID），可为空 */
    private Long leaderId;

    /** 部门类型：1部门 2科室 3小组 */
    @Min(value = 1, message = "部门类型只能是 1部门 2科室 3小组")
    @Max(value = 3, message = "部门类型只能是 1部门 2科室 3小组")
    private Integer deptType;

    private Integer sort;

    /** 状态：0禁用 1启用 */
    @Min(value = 0, message = "状态只能是 0禁用 1启用")
    @Max(value = 1, message = "状态只能是 0禁用 1启用")
    private Integer status;
}
