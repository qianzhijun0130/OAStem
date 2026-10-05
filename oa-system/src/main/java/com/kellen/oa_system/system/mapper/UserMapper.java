package com.kellen.oa_system.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.system.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper。复杂联表查询请使用 XML 或 @Select，避免把所有逻辑塞进 Wrapper。
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 统计工号占用情况，<b>包含已逻辑删除的用户</b>。
     *
     * <p>业务约定「员工离职、删除后工号不回收、不复用」，若沿用
     * {@code LambdaQueryWrapper} 会被 {@code @TableLogic} 自动追加
     * {@code is_deleted = 0}，从而把已删除用户的工号一并放行、造成复用。
     * 因此这里用原生 SQL 绕过逻辑删除，并支持排除自身（编辑场景）。</p>
     *
     * @param workNumber    待校验工号
     * @param excludeUserId 需要排除的用户ID，可为 null（新增场景）
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM oa_user WHERE work_number = #{workNumber}"
            + "<if test='excludeUserId != null'> AND id &lt;&gt; #{excludeUserId}</if>"
            + "</script>")
    Long countByWorkNumberIncludingDeleted(@Param("workNumber") String workNumber,
                                           @Param("excludeUserId") Long excludeUserId);
}
