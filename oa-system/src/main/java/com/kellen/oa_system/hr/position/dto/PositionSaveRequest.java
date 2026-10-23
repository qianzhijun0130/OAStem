package com.kellen.oa_system.hr.position.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 岗位新增 / 修改入参。
 *
 * <p>id 为空表示新增，非空表示修改。</p>
 */
@Data
public class PositionSaveRequest {

    /** 岗位ID；新增时为空 */
    private Long id;

    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 50, message = "岗位编码长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "岗位编码只能包含字母、数字、下划线与中划线")
    private String positionCode;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 100, message = "岗位名称长度不能超过 100")
    private String positionName;

    @NotNull(message = "所属部门不能为空")
    private Long deptId;

    /** 序列：M管理 P技术 S职能 O操作；留空按 P 处理 */
    @Pattern(regexp = "^[MPSO]$", message = "岗位序列只能是 M/P/S/O")
    private String series;

    @Size(max = 16, message = "职级长度不能超过 16")
    private String levelCode;

    /** 编制数，0 表示未设置（只做超编提示，不拦截） */
    @Min(value = 0, message = "编制数不能为负数")
    private Integer headcount;

    @Size(max = 500, message = "岗位说明长度不能超过 500")
    private String description;

    /** 状态：0停用 1启用 */
    @Min(value = 0, message = "状态只能是 0停用 1启用")
    @Max(value = 1, message = "状态只能是 0停用 1启用")
    private Integer status;

    private Integer sort;
}
