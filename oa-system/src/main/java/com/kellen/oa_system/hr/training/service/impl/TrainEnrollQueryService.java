package com.kellen.oa_system.hr.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.training.entity.TrainEnroll;
import com.kellen.oa_system.hr.training.entity.TrainSession;
import com.kellen.oa_system.hr.training.mapper.TrainEnrollMapper;
import com.kellen.oa_system.hr.training.mapper.TrainSessionMapper;
import com.kellen.oa_system.hr.training.support.TrainConstants;
import com.kellen.oa_system.hr.training.support.TrainEnrollSupport;
import com.kellen.oa_system.hr.training.support.TrainSessionSupport;
import com.kellen.oa_system.hr.training.vo.TrainEnrollVO;
import com.kellen.oa_system.hr.training.vo.TrainSessionVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报名查询领域（BE-OPT-08 二次拆分时从 {@code TrainEnrollService} 分出）。
 *
 * <p>拆出的理由：报名写入（含三条业务规则）与报名查询是两种变更节奏——
 * 前者改规则、后者改展示。866 行的原始文件里它们混在一起，改个列表列要翻过全部校验逻辑。</p>
 *
 * <p>不含事务注解，事务由门面持有。</p>
 */
@Service
@RequiredArgsConstructor
public class TrainEnrollQueryService {

    private final TrainEnrollMapper enrollMapper;
    private final TrainSessionMapper sessionMapper;
    private final TrainEnrollSupport enrollSupport;
    private final TrainSessionSupport sessionSupport;

    /** 某期次的花名册（HR 视角） */
    public List<TrainEnrollVO> listEnrolls(Long sessionId) {
        List<TrainEnroll> list = enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getSessionId, sessionId)
                .orderByAsc(TrainEnroll::getId));
        return enrollSupport.toVOs(list);
    }

    /** 我的培训记录（员工视角，按状态筛选） */
    public PageResult<TrainEnrollVO> myEnrolls(Integer status, Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        Long userId = SecurityContextUtil.getCurrentUserId();
        LambdaQueryWrapper<TrainEnroll> wrapper = new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getUserId, userId)
                .orderByDesc(TrainEnroll::getId);
        wrapper.eq(status != null, TrainEnroll::getStatus, status);
        Page<TrainEnroll> page = enrollMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(enrollSupport.toVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    /** 可报名期次：报名中的期次里，排除我已报过的 */
    public List<TrainSessionVO> myAvailableSessions() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        List<TrainSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<TrainSession>()
                .eq(TrainSession::getStatus, TrainConstants.SESSION_OPENING)
                .orderByAsc(TrainSession::getStartTime));
        Set<Long> enrolledIds = sessions.isEmpty() ? Set.of()
                : enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .eq(TrainEnroll::getUserId, userId)
                .in(TrainEnroll::getSessionId, sessions.stream().map(TrainSession::getId).toList()))
                .stream().map(TrainEnroll::getSessionId).collect(Collectors.toSet());
        List<TrainSession> available = sessions.stream()
                .filter(s -> !enrolledIds.contains(s.getId()))
                .toList();
        return sessionSupport.toVOs(available);
    }
}
