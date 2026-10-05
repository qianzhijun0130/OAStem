package com.kellen.oa_system.dashboard.mapper;

import com.kellen.oa_system.dashboard.vo.ChartItemVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作台统计查询。
 *
 * <p>为什么不用 MyBatis-Plus 的 QueryWrapper 拼聚合：分组统计的 SQL 一旦散在 Service 里，
 * 既难复用也难排查。集中放在 Mapper 用注解书写，Service 只负责组装与补零。</p>
 *
 * <p><b>注意</b>：自定义 SQL 不走 MyBatis-Plus 的逻辑删除插件，{@code is_deleted = 0}
 * 必须手写，否则会把已删除数据统计进来。</p>
 */
public interface DashboardMapper {

    /**
     * 近 N 个月审批单数量趋势。
     *
     * @param startTime 统计起始时间（含），由 Service 按「当月往前推 N-1 个月的 1 号」算出
     */
    @Select("""
            SELECT DATE_FORMAT(create_time, '%Y-%m') AS label, COUNT(*) AS total
            FROM oa_approval_instance
            WHERE is_deleted = 0 AND create_time >= #{startTime}
            GROUP BY DATE_FORMAT(create_time, '%Y-%m')
            ORDER BY label
            """)
    List<ChartItemVO> countMonthlyTrend(@Param("startTime") LocalDateTime startTime);

    /** 审批类型占比：按流程名称归类，流程被删时退化为流程编码 */
    @Select("""
            SELECT COALESCE(d.flow_name, i.flow_code) AS label, COUNT(*) AS total
            FROM oa_approval_instance i
            LEFT JOIN oa_flow_definition d ON d.flow_code = i.flow_code AND d.is_deleted = 0
            WHERE i.is_deleted = 0
            GROUP BY COALESCE(d.flow_name, i.flow_code)
            ORDER BY total DESC
            """)
    List<ChartItemVO> countTypeDistribution();
}
