package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.workflow.dto.ApprovalCcRequest;
import com.kellen.oa_system.workflow.entity.ApprovalCc;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.mapper.ApprovalCcMapper;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.support.ApprovalConstants;
import com.kellen.oa_system.workflow.support.ApprovalInstanceSupport;
import com.kellen.oa_system.workflow.support.ApprovalVoAssembler;
import com.kellen.oa_system.workflow.vo.ApprovalCcVO;
import com.kellen.oa_system.workflow.vo.CcUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 抄送：只用于告知，不产待办、不参与流转、不影响候选人。
 *
 * <p>同一单据对同一人只抄送一次（已抄送的自动跳过），并在同事务内写一条
 * {@code action='cc'} 的流转记录留痕。</p>
 *
 * <p>已读标记只允许操作自己的记录：越权访问按「不存在」处理，避免暴露他人数据的存在性。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalCcService {

    private final ApprovalCcMapper ccMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final ApprovalInstanceSupport instanceSupport;
    private final ApprovalVoAssembler assembler;

    public void cc(ApprovalCcRequest req) {
        Long operatorId = SecurityContextUtil.getCurrentUserId();
        ApprovalInstance inst = instanceSupport.getInst(req.getInstanceId());
        List<Long> targets = req.getUserIds().stream().filter(Objects::nonNull).distinct().toList();
        if (targets.isEmpty()) {
            return;
        }
        // 只保留真实存在的用户，避免脏 id 落库后详情查不到人
        List<Long> existing = userMapper.selectBatchIds(targets).stream().map(User::getId).toList();
        if (existing.isEmpty()) {
            return;
        }
        Set<Long> already = new HashSet<>();
        for (ApprovalCc c : ccMapper.selectList(new LambdaQueryWrapper<ApprovalCc>()
                .eq(ApprovalCc::getInstanceId, inst.getId())
                .in(ApprovalCc::getUserId, existing))) {
            already.add(c.getUserId());
        }
        int added = 0;
        for (Long uid : existing) {
            if (already.contains(uid)) {
                continue;
            }
            ApprovalCc cc = new ApprovalCc();
            cc.setInstanceId(inst.getId());
            cc.setUserId(uid);
            cc.setCcBy(operatorId);
            cc.setRemark(req.getRemark());
            cc.setReadFlag(0);
            ccMapper.insert(cc);
            added++;
        }
        if (added > 0) {
            instanceSupport.insertRecord(inst, "cc", operatorId, "抄送",
                    StringUtils.hasText(req.getRemark()) ? req.getRemark() : "抄送 " + added + " 人");
        }
    }

    public PageResult<ApprovalCcVO> ccPage(Integer readFlag, int pageNo, int pageSize) {
        pageNo = ApprovalVoAssembler.safePageNo(pageNo);
        pageSize = ApprovalVoAssembler.safePageSize(pageSize);
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<ApprovalCc> q = new LambdaQueryWrapper<ApprovalCc>()
                .eq(ApprovalCc::getUserId, userId)
                .orderByDesc(ApprovalCc::getId);
        if (readFlag != null) {
            q.eq(ApprovalCc::getReadFlag, readFlag);
        }
        long total = ccMapper.selectCount(q);
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        Page<ApprovalCc> page = new Page<>(pageNo, pageSize);
        ccMapper.selectPage(page, q);
        // 批量取单据、申请人组织信息、抄送人姓名，避免逐行查库
        Set<Long> instIds = new HashSet<>();
        Set<Long> byIds = new HashSet<>();
        for (ApprovalCc c : page.getRecords()) {
            instIds.add(c.getInstanceId());
            if (c.getCcBy() != null) {
                byIds.add(c.getCcBy());
            }
        }
        Map<Long, ApprovalInstance> instMap = new HashMap<>();
        for (ApprovalInstance inst : instanceMapper.selectBatchIds(instIds)) {
            instMap.put(inst.getId(), inst);
        }
        List<Long> applicants = new ArrayList<>();
        for (ApprovalInstance inst : instMap.values()) {
            applicants.add(inst.getApplicantId());
        }
        Map<Long, String[]> org = assembler.resolveOrg(applicants);
        Map<Long, String> byNames = new HashMap<>();
        if (!byIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(byIds)) {
                byNames.put(u.getId(), u.getRealName());
            }
        }
        List<ApprovalCcVO> vos = new ArrayList<>();
        for (ApprovalCc c : page.getRecords()) {
            ApprovalInstance inst = instMap.get(c.getInstanceId());
            if (inst == null) {
                continue;
            }
            ApprovalCcVO vo = new ApprovalCcVO();
            vo.setId(c.getId());
            vo.setInstanceId(inst.getId());
            vo.setTitle(inst.getTitle());
            vo.setFlowName(assembler.flowNameOf(inst));
            vo.setApplicantName(assembler.userName(inst.getApplicantId()));
            String[] names = org.get(inst.getApplicantId());
            if (names != null) {
                vo.setDeptName(names[0]);
                vo.setCompanyName(names[1]);
            }
            vo.setCurrentNode(inst.getCurrentNode());
            vo.setStatus(inst.getStatus());
            vo.setStatusName(ApprovalConstants.STATUS_NAME.get(inst.getStatus()));
            vo.setCcByName(byNames.get(c.getCcBy()));
            vo.setRemark(c.getRemark());
            vo.setReadFlag(c.getReadFlag());
            vo.setReadTime(assembler.fmt(c.getReadTime()));
            vo.setCreateTime(assembler.fmt(c.getCreateTime()));
            vos.add(vo);
        }
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    public void readCc(Long ccId) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ApprovalCc cc = ccMapper.selectById(ccId);
        // 只允许标记自己的抄送：越权访问按「不存在」处理，避免暴露他人数据存在性
        if (cc == null || !Objects.equals(cc.getUserId(), userId)) {
            throw new BizException(ErrorCode.INSTANCE_NOT_FOUND, "抄送记录不存在");
        }
        if (Objects.equals(cc.getReadFlag(), 1)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ccMapper.update(null, new LambdaUpdateWrapper<ApprovalCc>()
                .eq(ApprovalCc::getId, ccId)
                .set(ApprovalCc::getReadFlag, 1)
                .set(ApprovalCc::getReadTime, now)
                .set(ApprovalCc::getUpdateTime, now));
    }

    public void readAllCc() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        ccMapper.update(null, new LambdaUpdateWrapper<ApprovalCc>()
                .eq(ApprovalCc::getUserId, userId)
                .eq(ApprovalCc::getReadFlag, 0)
                .set(ApprovalCc::getReadFlag, 1)
                .set(ApprovalCc::getReadTime, now)
                .set(ApprovalCc::getUpdateTime, now));
    }

    public List<CcUserVO> ccUsers() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .orderByAsc(User::getId));
        Set<Long> deptIds = new HashSet<>();
        for (User u : users) {
            if (u.getDeptId() != null) {
                deptIds.add(u.getDeptId());
            }
        }
        Map<Long, String> deptNames = new HashMap<>();
        if (!deptIds.isEmpty()) {
            for (Department d : departmentMapper.selectBatchIds(deptIds)) {
                deptNames.put(d.getId(), d.getDeptName());
            }
        }
        List<CcUserVO> list = new ArrayList<>(users.size());
        for (User u : users) {
            CcUserVO vo = new CcUserVO();
            vo.setId(u.getId());
            vo.setRealName(u.getRealName());
            vo.setUsername(u.getUsername());
            vo.setDeptName(deptNames.get(u.getDeptId()));
            list.add(vo);
        }
        return list;
    }
}
