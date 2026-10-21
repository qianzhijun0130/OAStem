package com.kellen.oa_system.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 表单分页查询入参。
 */
@Data
public class FormPageQuery {

    /** 表单编码 / 名称 模糊关键字 */
    @Size(max = 64, message = "关键字长度不能超过 64")
    private String keyword;

    /** 1启用 0停用；不传表示不限 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;

    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 1000, message = "每页条数不能超过 1000")
    private Integer pageSize = 10;
}
