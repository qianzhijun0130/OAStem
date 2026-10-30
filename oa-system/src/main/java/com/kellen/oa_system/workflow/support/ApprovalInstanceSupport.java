package com.kellen.oa_system.workflow.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.ApprovalRecord;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.mapper.ApprovalRecordMapper;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审批单与流程模板的取数、状态与流转记录的写入口。
 *
 * <p>把「取实例 / 取模板 / 改状态 / 追加流转记录」收在一处，是因为它们对
 * {@code is_deleted} 与 {@code update_time} 有统一约定：
 * 实例必须过滤逻辑删、状态更新必须显式 SET（流程系列表的 {@code update_time} 没有
 * {@code ON UPDATE}，且 MP 的 NOT_NULL 策略下用实体回写会漏字段）。</p>
 */
@Component
@RequiredArgsConstructor
public class ApprovalInstanceSupport {

    private final ApprovalInstanceMapper instanceMapper;
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final ApprovalRecordMapper recordMapper;

    /** 取未删除的审批单；不存在即抛 6601 类错误，不区分「不存在」与「已删除」 */
    public ApprovalInstance getInst(Long id) {
        ApprovalInstance inst = instanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getId, id)
                .eq(ApprovalInstance::getIsDeleted, 0));
        if (inst == null) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND);
        }
        return inst;
    }

    public FlowDefinition getFlowDef(ApprovalInstance inst) {
        FlowDefinition fd = flowDefinitionMapper.selectById(inst.getFlowDefId());
        if (fd == null) {
            throw new BizException(ErrorCode.FLOW_DEF_NOT_FOUND);
        }
        return fd;
    }

    /** 取该审批单所属的流程定义；找不到返回 null（数据异常不应让详情页失败） */
    public FlowDefinition fdOf(ApprovalInstance inst) {
        return flowDefinitionMapper.selectById(inst.getFlowDefId());
    }

    /**
     * 仅更新实例状态。
     *
     * <p>显式 SET + 显式刷新 {@code update_time}：流程系列表该列没有 ON UPDATE，
     * 且 MP 的 NOT_NULL 策略下用实体回写会漏字段（D2 的教训）。</p>
     */
    public void saveStatus(ApprovalInstance inst, int status) {
        instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getId, inst.getId())
                .set(ApprovalInstance::getStatus, status)
                .set(ApprovalInstance::getUpdateTime, LocalDateTime.now()));
    }

    /** 追加一条流转记录（挂起/恢复/抄送这类不挂在具体任务节点上的动作） */
    public void insertRecord(ApprovalInstance inst, String action, Long handlerId,
                             String nodeName, String opinion) {
        ApprovalRecord rec = new ApprovalRecord();
        rec.setInstanceId(inst.getId());
        rec.setAction(action);
        rec.setHandlerId(handlerId);
        rec.setNodeName(nodeName);
        rec.setOpinion(opinion);
        recordMapper.insert(rec);
    }
}
