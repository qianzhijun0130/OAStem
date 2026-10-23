package com.kellen.oa_system.hr.position.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 岗位分页查询条件。
 */
@Data
public class PositionPageQuery {

    /** 关键词：岗位编码 / 岗位名称 模糊匹配 */
    private String keyword;

    private Long deptId;

    /** 序列：M/P/S/O */
    private String series;

    /** 状态：0停用 1启用 */
    private Integer status;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer pageNo = 1;

    @Min(value = 1, message = "每页条数至少 1")
    private Integer pageSize = 10;
}
