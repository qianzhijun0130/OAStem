package com.kellen.oa_system.notice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kellen.oa_system.notice.dto.MyNoticePageQuery;
import com.kellen.oa_system.notice.dto.NoticePageQuery;
import com.kellen.oa_system.notice.entity.Notice;
import com.kellen.oa_system.notice.vo.MyNoticeVO;
import com.kellen.oa_system.notice.vo.NoticeVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 公告查询。
 *
 * <p><b>可见性条件为什么一条 SQL 就够</b>：{@code FIND_IN_SET(NULL, x)} 在 MySQL 中返回 NULL
 * 而非报错，作为 WHERE 条件即视为「不成立」。所以当前用户没有部门时，「指定部门」那一支
 * 自然失效，不必写 {@code <if>} 分支——少一个分支就少一处漏判。</p>
 *
 * <p><b>注意</b>：自定义 SQL 不走 MyBatis-Plus 的逻辑删除插件，{@code is_deleted = 0} 必须手写。</p>
 */
public interface NoticeMapper extends BaseMapper<Notice> {

    /** 管理端分页：全状态可见，附发布人姓名与该公告的已读人数 */
    @Select("""
            <script>
            SELECT n.id, n.notice_title, n.notice_type, n.notice_level, n.notice_status,
                   n.top_flag, n.publish_scope, n.attachment_ids, n.publish_time,
                   n.publisher_id, n.view_count, n.create_time,
                   COALESCE(u.real_name, u.username) AS publisher_name,
                   (SELECT COUNT(*) FROM oa_notice_read r WHERE r.notice_id = n.id) AS read_count
            FROM oa_notice n
            LEFT JOIN oa_user u ON u.id = n.publisher_id
            WHERE n.is_deleted = 0
            <if test="query.noticeTitle != null and query.noticeTitle != ''">
                AND n.notice_title LIKE CONCAT('%', #{query.noticeTitle}, '%')
            </if>
            <if test="query.noticeType != null">
                AND n.notice_type = #{query.noticeType}
            </if>
            <if test="query.noticeLevel != null">
                AND n.notice_level = #{query.noticeLevel}
            </if>
            <if test="query.noticeStatus != null">
                AND n.notice_status = #{query.noticeStatus}
            </if>
            ORDER BY n.top_flag DESC, n.id DESC
            </script>
            """)
    IPage<NoticeVO> selectAdminPage(IPage<NoticeVO> page, @Param("query") NoticePageQuery query);

    /** 用户端分页：仅已发布且在发布范围内的公告，带当前用户的已读标记 */
    @Select("""
            <script>
            SELECT n.id, n.notice_title, n.notice_type, n.notice_level, n.top_flag,
                   n.publish_time, n.publisher_id,
                   COALESCE(u.real_name, u.username) AS publisher_name,
                   CASE WHEN r.id IS NULL THEN 0 ELSE 1 END AS read_flag,
                   CASE WHEN n.attachment_ids IS NULL OR n.attachment_ids = '' THEN 0 ELSE 1 END AS has_attachment
            FROM oa_notice n
            LEFT JOIN oa_user u ON u.id = n.publisher_id
            LEFT JOIN oa_notice_read r ON r.notice_id = n.id AND r.user_id = #{userId}
            WHERE n.is_deleted = 0
              AND n.notice_status = 1
              AND (n.publish_time IS NULL OR n.publish_time &lt;= NOW())
              AND (n.publish_scope = 1
                   OR (n.publish_scope = 2 AND FIND_IN_SET(#{deptId,jdbcType=BIGINT}, n.scope_dept_ids))
                   OR (n.publish_scope = 3 AND FIND_IN_SET(#{userId}, n.scope_user_ids)))
            <if test="query.noticeTitle != null and query.noticeTitle != ''">
                AND n.notice_title LIKE CONCAT('%', #{query.noticeTitle}, '%')
            </if>
            <if test="query.noticeType != null">
                AND n.notice_type = #{query.noticeType}
            </if>
            <if test="query.readFlag != null and query.readFlag == 0">
                AND r.id IS NULL
            </if>
            <if test="query.readFlag != null and query.readFlag == 1">
                AND r.id IS NOT NULL
            </if>
            ORDER BY n.top_flag DESC, n.publish_time DESC, n.id DESC
            </script>
            """)
    IPage<MyNoticeVO> selectVisiblePage(IPage<MyNoticeVO> page,
                                        @Param("userId") Long userId,
                                        @Param("deptId") Long deptId,
                                        @Param("query") MyNoticePageQuery query);

    /** 最新可见公告，供工作台使用（同样是范围过滤后的结果，不是全量最新） */
    @Select("""
            SELECT n.id, n.notice_title, n.notice_type, n.notice_level, n.top_flag,
                   n.publish_time, n.publisher_id,
                   COALESCE(u.real_name, u.username) AS publisher_name,
                   0 AS read_flag,
                   CASE WHEN n.attachment_ids IS NULL OR n.attachment_ids = '' THEN 0 ELSE 1 END AS has_attachment
            FROM oa_notice n
            LEFT JOIN oa_user u ON u.id = n.publisher_id
            WHERE n.is_deleted = 0
              AND n.notice_status = 1
              AND (n.publish_time IS NULL OR n.publish_time <= NOW())
              AND (n.publish_scope = 1
                   OR (n.publish_scope = 2 AND FIND_IN_SET(#{deptId,jdbcType=BIGINT}, n.scope_dept_ids))
                   OR (n.publish_scope = 3 AND FIND_IN_SET(#{userId}, n.scope_user_ids)))
            ORDER BY n.top_flag DESC, n.publish_time DESC, n.id DESC
            LIMIT #{limit}
            """)
    List<MyNoticeVO> selectVisibleList(@Param("userId") Long userId,
                                       @Param("deptId") Long deptId,
                                       @Param("limit") int limit);

    /** 未读数：可见范围内、已发布、且当前用户尚无阅读记录的公告数 */
    @Select("""
            SELECT COUNT(*)
            FROM oa_notice n
            LEFT JOIN oa_notice_read r ON r.notice_id = n.id AND r.user_id = #{userId}
            WHERE n.is_deleted = 0
              AND n.notice_status = 1
              AND (n.publish_time IS NULL OR n.publish_time <= NOW())
              AND r.id IS NULL
              AND (n.publish_scope = 1
                   OR (n.publish_scope = 2 AND FIND_IN_SET(#{deptId,jdbcType=BIGINT}, n.scope_dept_ids))
                   OR (n.publish_scope = 3 AND FIND_IN_SET(#{userId}, n.scope_user_ids)))
            """)
    long countUnread(@Param("userId") Long userId, @Param("deptId") Long deptId);
}
