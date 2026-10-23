package com.kellen.oa_system.hr.position.mapper;

import lombok.Data;

/**
 * 岗位在岗人数查询的投影行（仅内部使用，不对外暴露）。
 */
@Data
public class PositionDutyCountRow {

    private Long positionId;

    private Long onDutyCount;
}
