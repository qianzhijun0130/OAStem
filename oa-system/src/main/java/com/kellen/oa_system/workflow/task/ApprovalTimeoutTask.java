package com.kellen.oa_system.workflow.task;

import com.kellen.oa_system.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审批超时定时任务：每分钟扫描「已到期未处置」的审批单，按节点配置执行动作。
 *
 * <p>动作与幂等都在 {@link ApprovalService#applyTimeouts()} 里，本类只负责调度与
 * 「失败不打断下一轮」——异常在这里吞掉并记录，避免一次坏数据让任务永久停摆。</p>
 *
 * <p>与公告定时任务同节奏（每分钟第 0 秒），无处置时服务层直接返回 0、不打日志。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTimeoutTask {

    private final ApprovalService approvalService;

    @Scheduled(cron = "0 * * * * ?")
    public void applyTimeouts() {
        try {
            approvalService.applyTimeouts();
        } catch (Exception e) {
            log.error("审批超时任务执行失败，下一分钟重试", e);
        }
    }
}
