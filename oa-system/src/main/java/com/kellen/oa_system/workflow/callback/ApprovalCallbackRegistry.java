package com.kellen.oa_system.workflow.callback;

import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批终态回调分发器。
 *
 * <p>按 {@code flow_code} 找到唯一实现并调用；找不到实现时直接跳过——
 * 平台里绝大多数流程（如通用报销）没有业务回写需求，不应该因此报错。</p>
 *
 * <p><b>异常处理</b>：回调抛出的运行时异常会被记录后<b>继续向上抛</b>，
 * 从而让审批事务回滚。吞掉异常会造成「审批通过但额度没扣」的数据不一致，
 * 那比让用户重试一次糟糕得多。</p>
 */
@Slf4j
@Component
public class ApprovalCallbackRegistry {

    private final Map<String, ApprovalCallback> callbacks = new HashMap<>();

    public ApprovalCallbackRegistry(List<ApprovalCallback> callbackList) {
        for (ApprovalCallback callback : callbackList) {
            ApprovalCallback previous = callbacks.put(callback.flowCode(), callback);
            if (previous != null) {
                // 同一 flowCode 注册两个实现属于配置错误，启动时就暴露，别等线上跑偏
                throw new IllegalStateException("流程 " + callback.flowCode() + " 存在多个审批回调实现");
            }
        }
        log.info("审批回调已注册: {}", callbacks.keySet());
    }

    /**
     * 按终态分发。
     *
     * @param instance    审批实例
     * @param finalStatus 3 通过 / 4 驳回 / 5 撤销 / 6 终止
     */
    public void dispatch(ApprovalInstance instance, int finalStatus) {
        ApprovalCallback callback = callbacks.get(instance.getFlowCode());
        if (callback == null) {
            return;
        }
        log.info("审批终态回调: flowCode={} instanceId={} status={}",
                instance.getFlowCode(), instance.getId(), finalStatus);
        switch (finalStatus) {
            case 3 -> callback.onApproved(instance);
            case 4 -> callback.onRejected(instance);
            case 5 -> callback.onCancelled(instance);
            case 6 -> callback.onTerminated(instance);
            default -> log.debug("非终态不回调: status={}", finalStatus);
        }
    }
}
