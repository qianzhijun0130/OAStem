package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.workflow.entity.ApprovalInstance;
import com.kellen.oa_system.workflow.mapper.ApprovalInstanceMapper;
import com.kellen.oa_system.workflow.support.ApprovalConstants;
import com.kellen.oa_system.workflow.support.ApprovalVoAssembler;
import com.kellen.oa_system.workflow.vo.ApprovalInstanceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程实例总览（管理员视角，菜单 218）：标题 / 发起人 / 状态 / 流程编码 / 发起时间范围检索。
 *
 * <p>与「待办 / 已办 / 我的发起」分开放，是因为它们的权限模型不同：
 * 那三个是「当前用户的视角」，这个需要 {@code system:approval:instance}。
 * 混在一个类里时，最容易出现的错误是给总览页也套上「只看自己的」过滤。</p>
 *
 * <p>两个细节值得留意：</p>
 * <ul>
 *   <li>发起人姓名模糊查询<b>先把 LIKE 通配符转义</b>，否则用户输入一个 {@code %} 就会命中全表；</li>
 *   <li>时间范围格式非法<b>抛 400 而不是静默忽略</b>——静默忽略会让筛选失效并返回全量，
 *       用户以为筛过了，实际没有。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ApprovalInstanceQueryService {

    private final ApprovalInstanceMapper instanceMapper;
    private final UserMapper userMapper;
    private final ApprovalVoAssembler assembler;

    public PageResult<ApprovalInstanceVO> page(String title, String applicantName, Integer status,
                                               String flowCode, String beginTime, String endTime,
                                               int pageNo, int pageSize) {
        pageNo = ApprovalVoAssembler.safePageNo(pageNo);
        pageSize = ApprovalVoAssembler.safePageSize(pageSize);
        LambdaQueryWrapper<ApprovalInstance> q = new LambdaQueryWrapper<>();
        q.eq(ApprovalInstance::getIsDeleted, 0);
        if (status != null) {
            q.eq(ApprovalInstance::getStatus, status);
        }
        if (StringUtils.hasText(flowCode)) {
            q.eq(ApprovalInstance::getFlowCode, flowCode);
        }
        if (StringUtils.hasText(title)) {
            q.like(ApprovalInstance::getTitle, escapeLike(title));
        }
        if (StringUtils.hasText(applicantName)) {
            // 发起人姓名模糊：先按 real_name 命中用户，再用其 id 集合过滤实例。
            // 没有任何用户命中时直接返回空页，避免 in(空) 误查全表。
            List<Long> applicantIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .like(User::getRealName, escapeLike(applicantName)))
                    .stream().map(User::getId).toList();
            if (applicantIds.isEmpty()) {
                return PageResult.empty((long) pageNo, (long) pageSize);
            }
            q.in(ApprovalInstance::getApplicantId, applicantIds);
        }
        LocalDateTime begin = parseBeginTime(beginTime);
        LocalDateTime end = parseEndTime(endTime);
        if (begin != null) {
            q.ge(ApprovalInstance::getCreateTime, begin);
        }
        if (end != null) {
            q.le(ApprovalInstance::getCreateTime, end);
        }
        q.orderByDesc(ApprovalInstance::getId);
        long total = instanceMapper.selectCount(q);
        if (total == 0) {
            return PageResult.empty((long) pageNo, (long) pageSize);
        }
        Page<ApprovalInstance> page = new Page<>(pageNo, pageSize);
        instanceMapper.selectPage(page, q);
        List<ApprovalInstanceVO> vos = assembler.toInstanceVOs(page.getRecords());
        return PageResult.of(vos, total, (long) pageNo, (long) pageSize);
    }

    /** 时间范围入参解析（起始，缺时分秒补 00:00:00）；空值返回 null，格式非法抛 400 */
    private LocalDateTime parseBeginTime(String s) {
        return parseTime(s, " 00:00:00");
    }

    /** 时间范围入参解析（结束，缺时分秒补 23:59:59）；空值返回 null，格式非法抛 400 */
    private LocalDateTime parseEndTime(String s) {
        return parseTime(s, " 23:59:59");
    }

    /** 时间入参统一解析：{@code <=10} 字符按纯日期补足时分秒 */
    private LocalDateTime parseTime(String s, String timeSuffix) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            String v = s.length() <= 10 ? s + timeSuffix : s;
            return LocalDateTime.parse(v, ApprovalConstants.FMT);
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "时间格式不正确，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 转义 LIKE 通配符（{@code \ % _}）：避免用户输入被当作通配符（MySQL 默认以 {@code \} 为转义符） */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
