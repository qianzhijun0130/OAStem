package com.kellen.oa_system.notice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告新增 / 编辑请求。
 *
 * <p>保存后的状态一律为「草稿」，发布是独立的动作（见 {@code PUT /notice/{id}/publish}）。
 * 把「保存」与「发布」拆开，是为了让「先存草稿、确认无误再发」成为默认路径。</p>
 */
@Data
public class NoticeSaveRequest {

    /** 为空表示新增 */
    private Long id;

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "公告标题长度不能超过 200")
    private String noticeTitle;

    /** 公告类型：见数据字典 notice_type（1通知 2公告 3制度） */
    @NotNull(message = "公告类型不能为空")
    @Min(value = 1, message = "公告类型只能是 1通知 2公告 3制度")
    @Max(value = 3, message = "公告类型只能是 1通知 2公告 3制度")
    private Integer noticeType;

    /** 公告级别：见数据字典 notice_level（1普通 2重要 3紧急） */
    @NotNull(message = "公告级别不能为空")
    @Min(value = 1, message = "公告级别只能是 1普通 2重要 3紧急")
    @Max(value = 3, message = "公告级别只能是 1普通 2重要 3紧急")
    private Integer noticeLevel;

    @NotBlank(message = "公告内容不能为空")
    private String content;

    /** 1全部 2指定部门 3指定人员 */
    @NotNull(message = "发布范围不能为空")
    @Min(value = 1, message = "发布范围只能是 1全部 2指定部门 3指定人员")
    @Max(value = 3, message = "发布范围只能是 1全部 2指定部门 3指定人员")
    private Integer publishScope;

    /** publishScope=2 时必填 */
    private List<Long> scopeDeptIds;

    /** publishScope=3 时必填 */
    private List<Long> scopeUserIds;

    /** 附件对应的文件 ID 列表，来自 /file/upload 的返回 */
    private List<Long> attachmentIds;

    /** 0否 1是 */
    @Min(value = 0, message = "是否置顶只能是 0否 1是")
    @Max(value = 1, message = "是否置顶只能是 0否 1是")
    private Integer topFlag;

    /** 计划发布时间：到点由定时任务自动发布；为空表示不启用定时发布（仍可手工发布） */
    private LocalDateTime scheduledPublishTime;

    /** 自动下架时间：到点由定时任务自动下架；为空表示永不下架 */
    private LocalDateTime expireTime;
}
