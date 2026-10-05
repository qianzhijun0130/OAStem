package com.kellen.oa_system.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典类型新增 / 修改入参。id 为空表示新增。
 */
@Data
public class DictTypeSaveRequest {

    private Long id;

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100, message = "字典名称长度不能超过 100")
    private String dictName;

    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 100, message = "字典类型编码长度不能超过 100")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "字典类型编码只能包含字母、数字与下划线")
    private String dictType;

    /** 状态：0禁用 1启用，缺省 1 */
    @Min(value = 0, message = "状态只能是 0禁用 1启用")
    @Max(value = 1, message = "状态只能是 0禁用 1启用")
    private Integer status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
