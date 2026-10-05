package com.kellen.oa_system.notice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公告阅读记录（表 oa_notice_read）。
 *
 * <p>`uk_notice_user` 唯一索引保证同一用户对同一公告只有一条阅读记录，
 * 因此「标已读」用 INSERT IGNORE 语义即可，天然幂等。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_notice_read")
public class NoticeRead extends BaseEntity {

    private Long noticeId;

    private Long userId;

    private LocalDateTime readTime;
}
