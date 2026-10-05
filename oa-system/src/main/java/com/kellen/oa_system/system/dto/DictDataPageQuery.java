package com.kellen.oa_system.system.dto;

import lombok.Data;

/**
 * 字典数据项分页查询入参。
 */
@Data
public class DictDataPageQuery {

    private Integer pageNo = 1;

    private Integer pageSize = 10;

    /** 所属字典类型编码（精确匹配，用于右侧面板按类型展示） */
    private String dictType;

    /** 显示名称，模糊匹配 */
    private String itemLabel;

    /** 状态：0禁用 1启用 */
    private Integer status;
}
