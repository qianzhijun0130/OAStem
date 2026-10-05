package com.kellen.oa_system.system.dto;

import lombok.Data;

/**
 * 字典类型分页查询入参。
 */
@Data
public class DictTypePageQuery {

    private Integer pageNo = 1;

    private Integer pageSize = 10;

    /** 字典名称，模糊匹配 */
    private String dictName;

    /** 字典类型编码，模糊匹配 */
    private String dictType;

    /** 状态：0禁用 1启用 */
    private Integer status;
}
