package com.kellen.oa_system.notice.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kellen.oa_system.notice.entity.Notice;
import com.kellen.oa_system.notice.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 公告定时任务：每整分钟扫描一次，处理两类到点动作。
 *
 * <ol>
 *   <li><b>定时发布</b>：草稿态且 {@code scheduled_publish_time <= now} → 置为已发布并写入实际发布时间；</li>
 *   <li><b>自动下架</b>：已发布且 {@code expire_time <= now} → 置为已下架。</li>
 * </ol>
 *
 * <p>状态常量与 {@code NoticeServiceImpl} 保持一致（0草稿 1已发布 2已下架）。两处都定义而非
 * 互相引用，是为了避免任务类反向依赖服务实现；若状态口径调整，需同步修改。</p>
 *
 * <p>无变化时不打日志，避免每分钟刷屏。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeScheduleTask {

    /** 0草稿 */
    private static final int STATUS_DRAFT = 0;
    /** 1已发布 */
    private static final int STATUS_PUBLISHED = 1;
    /** 2已下架 */
    private static final int STATUS_OFFLINE = 2;

    private final NoticeMapper noticeMapper;

    /** 每分钟第 0 秒执行 */
    @Scheduled(cron = "0 * * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void applyNoticeSchedule() {
        LocalDateTime now = LocalDateTime.now();

        int published = noticeMapper.update(null, new LambdaUpdateWrapper<Notice>()
                .set(Notice::getNoticeStatus, STATUS_PUBLISHED)
                // 与手工发布同口径：截断到秒，避免 datetime 列的四舍五入把发布时间进位到下一秒
                // 而令公告短暂「发布后不可见」
                .set(Notice::getPublishTime, now.truncatedTo(ChronoUnit.SECONDS))
                .eq(Notice::getNoticeStatus, STATUS_DRAFT)
                .isNotNull(Notice::getScheduledPublishTime)
                .le(Notice::getScheduledPublishTime, now));

        int offlined = noticeMapper.update(null, new LambdaUpdateWrapper<Notice>()
                .set(Notice::getNoticeStatus, STATUS_OFFLINE)
                .eq(Notice::getNoticeStatus, STATUS_PUBLISHED)
                .isNotNull(Notice::getExpireTime)
                .le(Notice::getExpireTime, now));

        if (published > 0 || offlined > 0) {
            log.info("公告定时任务执行：自动发布 {} 条，自动下架 {} 条", published, offlined);
        }
    }
}
