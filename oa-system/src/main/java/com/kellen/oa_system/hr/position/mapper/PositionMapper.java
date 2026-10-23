package com.kellen.oa_system.hr.position.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.hr.position.entity.Position;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 岗位 Mapper。
 */
public interface PositionMapper extends BaseMapper<Position> {

    /**
     * 批量统计岗位在岗人数。
     *
     * <p>一次分组查询解决列表页的「在岗人数」列，避免按行逐条 count 造成 N+1。
     * 在岗口径与花名册一致：{@code employment_status IN (2,3,4)}，且不受账号状态影响。</p>
     *
     * @param ids 岗位ID集合，调用方需保证非空
     */
    @Select("<script>"
            + "SELECT position_id AS positionId, COUNT(*) AS onDutyCount FROM oa_user"
            + " WHERE is_deleted = 0 AND employment_status IN (2, 3, 4)"
            + " AND position_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " GROUP BY position_id"
            + "</script>")
    List<PositionDutyCountRow> countOnDuty(@Param("ids") List<Long> ids);
}
