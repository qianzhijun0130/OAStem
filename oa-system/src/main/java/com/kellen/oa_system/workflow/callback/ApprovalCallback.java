package com.kellen.oa_system.workflow.callback;

import com.kellen.oa_system.workflow.entity.ApprovalInstance;

/**
 * 审批终态回调扩展点。
 *
 * <p><b>为什么需要它</b>：引擎只维护 {@code oa_approval_instance.status}，不会回调业务表。
 * 请假、加班、离职等业务如果各写一套「审批后处理」，会出现 5 份相似的幂等逻辑、
 * 其中 2 份漏掉撤销分支。统一成 SPI 后，业务方只需实现本接口并声明 {@link #flowCode()}。</p>
 *
 * <p><b>实现约定（必须遵守）</b>：</p>
 * <ol>
 *   <li><b>必须幂等</b>：同一实例可能被重复回调（重试/补偿），先判断业务状态再处理；</li>
 *   <li><b>必须抛异常表示失败</b>：回调在审批事务内执行，抛异常会让审批动作整体回滚，
 *       不会出现「审批通过但业务没落库」；</li>
 *   <li><b>只依赖 Mapper 或独立服务</b>：不要反向依赖审批服务本身，否则形成循环依赖。</li>
 * </ol>
 */
public interface ApprovalCallback {

    /** 支持的流程编码，如 {@code HR_LEAVE}；与 {@code oa_flow_definition.flow_code} 对应 */
    String flowCode();

    /** 审批通过（终态 3） */
    default void onApproved(ApprovalInstance instance) {
    }

    /** 审批驳回（终态 4） */
    default void onRejected(ApprovalInstance instance) {
    }

    /** 发起人撤销（终态 5） */
    default void onCancelled(ApprovalInstance instance) {
    }

    /** 管理员终止（终态 6） */
    default void onTerminated(ApprovalInstance instance) {
    }
}
