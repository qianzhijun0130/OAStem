package com.kellen.oa_system.notice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kellen.oa_system.common.entity.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公告通知实体，对应表 oa_notice。
 *
 * <p>本次仅用于工作台「最新公告」卡片，完整的发布/编辑/已读功能在第二轮公告模块中补齐。</p>
 *
 * <p>字段约定：{@code publishScope} 1全公司 2指定部门 3指定人员；{@code noticeStatus} 0草稿 1已发布；
 * 范围字段（scopeDeptIds / scopeUserIds）以逗号分隔的 id 串存储，与前端多选组件形态一致。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_notice")
public class Notice extends AuditEntity {

    private String noticeTitle;

    /** 公告类型：见数据字典 notice_type */
    private Integer noticeType;

    /** 公告级别：1普通 2重要 3紧急，见数据字典 notice_level */
    private Integer noticeLevel;

    private String content;

    /** 附件文件 id，逗号分隔 */
    private String attachmentIds;

    /** 发布范围：1全公司 2指定部门 3指定人员 */
    private Integer publishScope;

    private String scopeDeptIds;

    private String scopeUserIds;

    /** 0草稿 1已发布 2已下架 */
    private Integer noticeStatus;

    /** 计划发布时间：到点由定时任务自动发布；为空表示不启用定时发布 */
    private LocalDateTime scheduledPublishTime;

    /** 自动下架时间：到点由定时任务自动下架；为空表示永不下架 */
    private LocalDateTime expireTime;

    private LocalDateTime publishTime;

    private Long publisherId;

    private Integer viewCount;

    /** 1置顶 0普通 */
    private Integer topFlag;
}
