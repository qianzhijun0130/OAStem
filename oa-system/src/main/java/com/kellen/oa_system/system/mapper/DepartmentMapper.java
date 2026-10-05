package com.kellen.oa_system.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.system.entity.Department;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 部门 Mapper。
 *
 * <p>工号取号相关的三条 SQL 刻意写成原生语句而非 Wrapper：{@code FOR UPDATE}
 * 行锁与「只更新指定列」的语义用 Wrapper 表达既啰嗦又容易踩到
 * {@code @TableLogic} 自动追加条件、{@code updateById} 忽略 null 字段等坑。</p>
 */
public interface DepartmentMapper extends BaseMapper<Department> {

    /**
     * 锁定部门行并读取当前工号流水号。
     *
     * <p><b>必须在事务中调用</b>：行锁随事务结束释放，脱离事务调用等于没有加锁。</p>
     */
    @Select("SELECT work_no_seq FROM oa_department WHERE id = #{id} FOR UPDATE")
    Integer selectWorkNoSeqForUpdate(@Param("id") Long id);

    /** 推进部门工号流水号（只增不减，工号不回收、不复用） */
    @Update("UPDATE oa_department SET work_no_seq = #{seq} WHERE id = #{id}")
    int updateWorkNoSeq(@Param("id") Long id, @Param("seq") Integer seq);

    /** 回写自动推导出的工号前缀 */
    @Update("UPDATE oa_department SET work_no_prefix = #{prefix} WHERE id = #{id}")
    int updateWorkNoPrefix(@Param("id") Long id, @Param("prefix") String prefix);

    /** 统计除本部门外还有多少部门占用了该工号前缀，用于前缀冲突时追加序号 */
    @Select("SELECT COUNT(*) FROM oa_department WHERE work_no_prefix = #{prefix} "
            + "AND is_deleted = 0 AND id <> #{excludeId}")
    Long countByWorkNoPrefix(@Param("prefix") String prefix, @Param("excludeId") Long excludeId);
}
