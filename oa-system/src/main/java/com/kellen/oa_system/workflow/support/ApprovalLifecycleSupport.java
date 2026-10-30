package com.kellen.oa_system.workflow.support;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kellen.oa_system.workflow.callback.ApprovalCallbackRegistry;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * <b>审批终态的唯一收敛点。</b>
 *
 * <p>四个入口全部走这里：审批驳回（{@code complete}）、发起人撤销（{@code withdraw}）、
 * 管理员终止（{@code terminate}）、超时自动驳回（{@code applyTimeouts}）。
 * 之所以坚持「只有这一个方法能把实例置为终态」，是因为它同时承担业务回写：
 * {@link ApprovalCallbackRegistry#dispatch} 会把请假/加班/出差/补卡的业务表改到终态。</p>
 *
 * <p>如果哪天有人图省事在某个新入口里自己写一句 {@code update status}，
 * 那条路径的业务表就不会被回写——表现是「审批已通过，但请假单还在审批中」，
 * 而且只在某一类操作后出现，极难归因。改这里之前请先确认：你是不是真的需要一个新的终态入口。</p>
 *
 * <p><b>为什么必须用 UpdateWrapper 而不能 {@code setCurrentNode(null) + updateById}</b>：
 * MyBatis-Plus 的 {@code updateById} 采用 NOT_NULL 更新策略——实体里为 null 的字段
 * 不会进 UPDATE 的 SET 段，{@code setCurrentNode(null)} 会被静默跳过，
 * 终结实例的 {@code current_node} 因而残留旧节点名（D2 缺陷）。只有 UpdateWrapper
 * 的显式 {@code SET} 才能写 NULL。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalLifecycleSupport {

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalCallbackRegistry approvalCallbackRegistry;

    /**
     * 把审批实例置为终结态（已通过 / 已驳回 / 已撤销 / 已终止），清空当前节点与超时计时，
     * 并在同一事务内分发业务回调。
     *
     * <p>回调失败会让审批动作一起回滚，避免「审批通过了但业务表还是审批中」。</p>
     */
    public void finish(ApprovalInstance inst, int finalStatus) {
        instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getId, inst.getId())
                .set(ApprovalInstance::getStatus, finalStatus)
                .set(ApprovalInstance::getCurrentNode, null)
                // 流程结束不再有「当前节点」，超时计时一并清空
                .set(ApprovalInstance::getDueTime, null)
                .set(ApprovalInstance::getUpdateTime, LocalDateTime.now()));
        // 业务回写：实例对象上的状态同步更新，供回调判断（回调读的是入参 inst，不是库里那份）
        inst.setStatus(finalStatus);
        inst.setCurrentNode(null);
        approvalCallbackRegistry.dispatch(inst, finalStatus);
    }
}
