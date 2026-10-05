package com.kellen.oa_system.notice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.notice.entity.NoticeRead;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 公告阅读记录。
 *
 * <p>`uk_notice_user` 唯一索引保证同一用户对同一公告只有一条阅读记录，因此「标已读」用
 * {@link #insertIgnore} 的 INSERT IGNORE 语义即可，天然幂等——并发写入时冲突被数据库降级为
 * 「无操作」，不会抛 `DuplicateKeyException` 拖垮整个详情请求。</p>
 */
public interface NoticeReadMapper extends BaseMapper<NoticeRead> {

    /**
     * 幂等写入阅读记录：冲突（同一 notice_id + user_id 已存在）时忽略，返回 0。
     *
     * <p>刻意手写 SQL 而非走 {@code BaseMapper#insert}：后者在冲突时抛异常，而「已读」这个动作
     * 的目标状态是「记录存在」，已经存在就等同于成功，不应该被当成错误。</p>
     */
    @Insert("INSERT IGNORE INTO oa_notice_read (notice_id, user_id, read_time) "
            + "VALUES (#{noticeId}, #{userId}, #{readTime})")
    int insertIgnore(@Param("noticeId") Long noticeId,
                     @Param("userId") Long userId,
                     @Param("readTime") LocalDateTime readTime);
}
