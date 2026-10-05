package com.kellen.oa_system.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典数据项新增 / 修改入参。id 为空表示新增。
 */
@Data
public class DictDataSaveRequest {

    private Long id;

    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 100, message = "字典类型编码长度不能超过 100")
    private String dictType;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 100, message = "显示名称长度不能超过 100")
    private String itemLabel;

    @NotBlank(message = "数据值不能为空")
    @Size(max = 100, message = "数据值长度不能超过 100")
    private String itemValue;

    private Integer sort;

    /** 状态：0禁用 1启用，缺省 1 */
    @Min(value = 0, message = "状态只能是 0禁用 1启用")
    @Max(value = 1, message = "状态只能是 0禁用 1启用")
    private Integer status;

    @Size(max = 50, message = "标签样式长度不能超过 50")
    private String cssClass;

    /** 是否为该类型的默认项：1是 0否 */
    @Min(value = 0, message = "是否默认只能是 0否 1是")
    @Max(value = 1, message = "是否默认只能是 0否 1是")
    private Integer isDefault;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
