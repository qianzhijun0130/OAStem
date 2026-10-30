package com.kellen.oa_system.workflow.support;

import com.kellen.oa_system.system.entity.Company;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.CompanyMapper;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.entity.ApprovalRecord;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.vo.ApprovalInstanceVO;
import com.kellen.oa_system.workflow.vo.ApprovalRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 审批 VO 组装（实例 / 流转记录）与「用户 → 部门 / 公司」批量解析。
 *
 * <p>列表页每行都要显示部门与公司，逐行查库会变成 3N 次查询，所以统一按申请人集合批量解析；
 * 单据、流程名、处理人姓名也由调用方批量预取后传进来，取不到才回退单查。</p>
 */
@Component
@RequiredArgsConstructor
public class ApprovalVoAssembler {

    private final ApprovalInstanceMapper instanceMapper;
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final CompanyMapper companyMapper;

    public ApprovalInstanceVO toInstanceVO(ApprovalInstance inst) {
        ApprovalInstanceVO vo = new ApprovalInstanceVO();
        vo.setId(inst.getId());
        vo.setFlowCode(inst.getFlowCode());
        vo.setBillCode(inst.getBillCode());
        vo.setFlowName(flowNameOf(inst));
        vo.setTitle(inst.getTitle());
        vo.setApplicantId(inst.getApplicantId());
        vo.setApplicantName(userName(inst.getApplicantId()));
        vo.setStatus(inst.getStatus());
        vo.setStatusName(ApprovalConstants.STATUS_NAME.get(inst.getStatus()));
        vo.setCurrentNode(inst.getCurrentNode());
        vo.setFormData(inst.getFormData());
        vo.setCreateTime(fmt(inst.getCreateTime()));
        if (inst.getDueTime() != null) {
            vo.setDueTime(ApprovalConstants.FMT.format(inst.getDueTime()));
            // 仅「审批中」才可能逾期：挂起/已结束都不再计时
            vo.setOverdue(Objects.equals(inst.getStatus(), ApprovalConstants.STATUS_APPROVING)
                    && inst.getDueTime().isBefore(LocalDateTime.now()));
        } else {
            vo.setOverdue(false);
        }
        return vo;
    }

    /**
     * 批量转 VO，并一次性补齐发起人的部门与公司。
     *
     * <p>列表页每行都要显示这两个字段，逐行查库会变成 3N 次查询，故按申请人集合批量解析。</p>
     */
    public List<ApprovalInstanceVO> toInstanceVOs(List<ApprovalInstance> list) {
        List<Long> applicants = new ArrayList<>();
        for (ApprovalInstance inst : list) {
            applicants.add(inst.getApplicantId());
        }
        Map<Long, String[]> org = resolveOrg(applicants);
        List<ApprovalInstanceVO> vos = new ArrayList<>(list.size());
        for (ApprovalInstance inst : list) {
            ApprovalInstanceVO vo = toInstanceVO(inst);
            String[] names = org.get(inst.getApplicantId());
            if (names != null) {
                vo.setDeptName(names[0]);
                vo.setCompanyName(names[1]);
            }
            vos.add(vo);
        }
        return vos;
    }

    /** 单条记录转 VO（详情时间轴用）：自行取单据，适合数据量小的场景 */
    public ApprovalRecordVO toRecordVO(ApprovalRecord r) {
        return toRecordVO(r, instanceMapper.selectById(r.getInstanceId()));
    }

    /**
     * 列表用：单据、流程名、处理人姓名全部由调用方批量预取。
     *
     * <p>取不到时回退单查——保证单条调用的路径（{@code detail} 等）行为不变，
     * 也让预取缺失不会导致字段变空。</p>
     */
    public ApprovalRecordVO toRecordVO(ApprovalRecord r, ApprovalInstance preloaded,
                                       Map<Long, String> flowNames, Map<Long, String> handlerNames) {
        ApprovalRecordVO vo = new ApprovalRecordVO();
        vo.setInstanceId(r.getInstanceId());
        ApprovalInstance inst = preloaded != null ? preloaded : instanceMapper.selectById(r.getInstanceId());
        vo.setTitle(inst != null ? inst.getTitle() : null);
        String flowName = null;
        if (inst != null && inst.getFlowDefId() != null) {
            flowName = flowNames.get(inst.getFlowDefId());
        }
        vo.setFlowName(flowName != null ? flowName : flowNameOf(inst));
        vo.setNodeName(r.getNodeName());
        String handlerName = handlerNames.get(r.getHandlerId());
        vo.setHandlerName(handlerName != null ? handlerName : userName(r.getHandlerId()));
        vo.setAction(r.getAction());
        vo.setActionName(ApprovalConstants.ACTION_NAME.get(r.getAction()));
        vo.setOpinion(r.getOpinion());
        vo.setCreateTime(fmt(r.getCreateTime()));
        return vo;
    }

    /** 列表用：单据由调用方批量预取后传入，避免逐条查询 */
    public ApprovalRecordVO toRecordVO(ApprovalRecord r, ApprovalInstance preloaded) {
        ApprovalRecordVO vo = new ApprovalRecordVO();
        vo.setInstanceId(r.getInstanceId());
        ApprovalInstance inst = preloaded != null ? preloaded : instanceMapper.selectById(r.getInstanceId());
        vo.setTitle(inst != null ? inst.getTitle() : null);
        vo.setFlowName(flowNameOf(inst));
        vo.setNodeName(r.getNodeName());
        vo.setHandlerName(userName(r.getHandlerId()));
        vo.setAction(r.getAction());
        vo.setActionName(ApprovalConstants.ACTION_NAME.get(r.getAction()));
        vo.setOpinion(r.getOpinion());
        vo.setCreateTime(fmt(r.getCreateTime()));
        return vo;
    }

    public String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        User u = userMapper.selectById(userId);
        return u != null ? u.getRealName() : null;
    }

    public String flowNameOf(ApprovalInstance inst) {
        if (inst == null) {
            return null;
        }
        if (inst.getFlowDefId() != null) {
            FlowDefinition fd = flowDefinitionMapper.selectById(inst.getFlowDefId());
            if (fd != null) {
                return fd.getFlowName();
            }
        }
        return inst.getFlowCode();
    }

    /**
     * 批量解析「用户 → 所在部门 / 所属公司」。
     *
     * <p>用户 → 部门 → 公司各查一次（3 条 SQL），在内存里拼装；
     * 查不到的项留 null，由前端决定显示 {@code -}。</p>
     *
     * @return userId → [deptName, companyName]
     */
    public Map<Long, String[]> resolveOrg(List<Long> userIds) {
        Map<Long, String[]> org = new HashMap<>();
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return org;
        }
        List<User> users = userMapper.selectBatchIds(ids);
        if (users.isEmpty()) {
            return org;
        }
        Set<Long> deptIds = new HashSet<>();
        for (User u : users) {
            if (u.getDeptId() != null) {
                deptIds.add(u.getDeptId());
            }
        }
        Map<Long, Department> deptMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            for (Department d : departmentMapper.selectBatchIds(deptIds)) {
                deptMap.put(d.getId(), d);
            }
        }
        Set<Long> companyIds = new HashSet<>();
        for (Department d : deptMap.values()) {
            if (d.getCompanyId() != null) {
                companyIds.add(d.getCompanyId());
            }
        }
        Map<Long, String> companyMap = new HashMap<>();
        if (!companyIds.isEmpty()) {
            for (Company c : companyMapper.selectBatchIds(companyIds)) {
                companyMap.put(c.getId(), c.getCompanyName());
            }
        }
        for (User u : users) {
            Department d = u.getDeptId() == null ? null : deptMap.get(u.getDeptId());
            org.put(u.getId(), d == null
                    ? new String[]{null, null}
                    : new String[]{d.getDeptName(), companyMap.get(d.getCompanyId())});
        }
        return org;
    }

    public String fmt(LocalDateTime ldt) {
        return ldt == null ? null : ApprovalConstants.FMT.format(ldt);
    }

    public String fmt(Date date) {
        return date == null ? null
                : ApprovalConstants.FMT.format(LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    /** 分页入参归一：页码 <1 归 1（过大由 MyBatis-Plus 全局 maxLimit=1000 截断） */
    public static int safePageNo(int pageNo) {
        return pageNo < 1 ? 1 : pageNo;
    }

    /** 分页入参归一：页大小 <1 归默认 10 */
    public static int safePageSize(int pageSize) {
        return pageSize < 1 ? 10 : pageSize;
    }
}
