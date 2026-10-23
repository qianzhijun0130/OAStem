package com.kellen.oa_system.hr.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.hr.leave.entity.LeaveQuota;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 假期额度 Mapper。
 *
 * <p><b>为什么额度变更全用条件 UPDATE</b>：`先查余额再写回` 在并发下必然超卖
 * （两个请求各读到「还剩 1 天」都通过了）。这里的 SQL 把「够不够」写进 WHERE，
 * 靠影响行数判断成功——影响 0 行就是额度不足，不需要额外加锁。</p>
 */
public interface LeaveQuotaMapper extends BaseMapper<LeaveQuota> {

    /**
     * 占用额度（审批中）。返回 0 表示可用额度不足。
     */
    @Update("UPDATE oa_leave_quota SET locked_days = locked_days + #{days}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND leave_type = #{leaveType} AND year = #{year} "
            + "AND is_deleted = 0 "
            + "AND total_days + carry_over_days - used_days - locked_days >= #{days}")
    int lock(@Param("userId") Long userId,
             @Param("leaveType") Integer leaveType,
             @Param("year") Integer year,
             @Param("days") BigDecimal days);

    /**
     * 释放占用（驳回 / 撤销）。用 GREATEST 兜底，避免脏数据把 locked 减成负数。
     */
    @Update("UPDATE oa_leave_quota SET locked_days = GREATEST(locked_days - #{days}, 0), update_time = NOW() "
            + "WHERE user_id = #{userId} AND leave_type = #{leaveType} AND year = #{year} AND is_deleted = 0")
    int release(@Param("userId") Long userId,
                @Param("leaveType") Integer leaveType,
                @Param("year") Integer year,
                @Param("days") BigDecimal days);

    /**
     * 占用转已用（审批通过）。
     */
    @Update("UPDATE oa_leave_quota SET used_days = used_days + #{days}, "
            + "locked_days = GREATEST(locked_days - #{days}, 0), update_time = NOW() "
            + "WHERE user_id = #{userId} AND leave_type = #{leaveType} AND year = #{year} AND is_deleted = 0")
    int consume(@Param("userId") Long userId,
                @Param("leaveType") Integer leaveType,
                @Param("year") Integer year,
                @Param("days") BigDecimal days);

    /**
     * 销假差额退回（已用减少）。
     */
    @Update("UPDATE oa_leave_quota SET used_days = GREATEST(used_days - #{days}, 0), update_time = NOW() "
            + "WHERE user_id = #{userId} AND leave_type = #{leaveType} AND year = #{year} AND is_deleted = 0")
    int refund(@Param("userId") Long userId,
               @Param("leaveType") Integer leaveType,
               @Param("year") Integer year,
               @Param("days") BigDecimal days);

    /**
     * 加班转调休：直接累加额度（不需要先占用）。
     */
    @Update("UPDATE oa_leave_quota SET total_days = total_days + #{days}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND leave_type = #{leaveType} AND year = #{year} AND is_deleted = 0")
    int increaseTotal(@Param("userId") Long userId,
                      @Param("leaveType") Integer leaveType,
                      @Param("year") Integer year,
                      @Param("days") BigDecimal days);
}
