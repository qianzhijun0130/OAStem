package com.kellen.oa_system.hr.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.recruit.entity.RecruitCandidate;
import com.kellen.oa_system.hr.recruit.entity.RecruitDemand;
import com.kellen.oa_system.hr.recruit.entity.RecruitInterview;
import com.kellen.oa_system.hr.recruit.entity.RecruitPost;
import com.kellen.oa_system.hr.recruit.mapper.RecruitCandidateMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitDemandMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitInterviewMapper;
import com.kellen.oa_system.hr.recruit.mapper.RecruitPostMapper;
import com.kellen.oa_system.hr.recruit.support.RecruitConstants;
import com.kellen.oa_system.hr.recruit.vo.RecruitBoardVO;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 招聘看板：在招规模、阶段漏斗、渠道转化、淘汰分布、部门维度。
 *
 * <p>所有指标都在内存里对「本次取回的全量候选人」做聚合，不做逐条 count。
 * 数据量级是「在招候选人几百条」，一次全取比几十次条件统计更划算。
 * 注意渠道转化里的面试数仍是按渠道逐个统计的（{@code interviewMapper.selectCount}），
 * 共 5 次，可接受。</p>
 */
@Service
@RequiredArgsConstructor
public class RecruitBoardService {

    private final RecruitPostMapper postMapper;
    private final RecruitDemandMapper demandMapper;
    private final RecruitCandidateMapper candidateMapper;
    private final RecruitInterviewMapper interviewMapper;
    private final DepartmentMapper departmentMapper;

    public RecruitBoardVO board(Long deptId) {
        RecruitBoardVO vo = new RecruitBoardVO();

        List<RecruitPost> posts = postMapper.selectList(new LambdaQueryWrapper<RecruitPost>()
                .eq(RecruitPost::getStatus, 2));
        List<RecruitDemand> demands = demandMapper.selectList(new LambdaQueryWrapper<RecruitDemand>()
                .in(RecruitDemand::getStatus,
                        List.of(RecruitConstants.DEMAND_RECRUITING, RecruitConstants.DEMAND_FINISHED)));
        if (deptId != null) {
            demands = demands.stream().filter(d -> Objects.equals(d.getDeptId(), deptId)).toList();
        }
        List<RecruitCandidate> candidates = candidateMapper.selectList(
                new LambdaQueryWrapper<RecruitCandidate>());

        vo.setPostingCount(posts.size());
        vo.setDemandHeadCount(demands.stream().map(d -> d.getHeadCount() == null ? 0 : d.getHeadCount())
                .reduce(0, Integer::sum));
        vo.setHiredCount((int) candidates.stream()
                .filter(c -> Objects.equals(c.getStage(), RecruitConstants.STAGE_HIRED)).count());

        // 平均招聘周期：审批通过 → 完成
        List<Long> cycles = demands.stream()
                .filter(d -> d.getApproveTime() != null && d.getFinishTime() != null)
                .map(d -> ChronoUnit.DAYS.between(d.getApproveTime().toLocalDate(), d.getFinishTime().toLocalDate()))
                .toList();
        vo.setAvgCycleDays(cycles.isEmpty() ? 0
                : (int) Math.round(cycles.stream().mapToLong(Long::longValue).average().orElse(0)));

        // 阶段漏斗
        List<RecruitBoardVO.StageCount> funnel = new ArrayList<>();
        int[] funnelStages = {1, 2, 3, 4, 5, 6, 7};
        int prev = 0;
        for (int stage : funnelStages) {
            RecruitBoardVO.StageCount item = new RecruitBoardVO.StageCount();
            item.setStage(stage);
            item.setLabel(RecruitConstants.STAGE_LABEL.get(stage));
            int count = (int) candidates.stream().filter(c -> Objects.equals(c.getStage(), stage)).count();
            item.setCount(count);
            // 转化率：相对上一阶段
            item.setConvertRate(prev == 0 ? 100 : (count * 100) / prev);
            prev = count;
            funnel.add(item);
        }
        vo.setStageFunnel(funnel);

        // 渠道转化
        List<RecruitBoardVO.ChannelStat> channels = new ArrayList<>();
        for (int s = 1; s <= 5; s++) {
            // ⚠️ 循环变量不是 effectively final，lambda 里不能直接捕获
            final int source = s;
            RecruitBoardVO.ChannelStat stat = new RecruitBoardVO.ChannelStat();
            stat.setSource(source);
            stat.setLabel(RecruitConstants.SOURCE_LABEL.get(source));
            List<RecruitCandidate> list = candidates.stream()
                    .filter(c -> Objects.equals(c.getSource(), source)).toList();
            stat.setResumeCount(list.size());
            Set<Long> ids = list.stream().map(RecruitCandidate::getId).collect(Collectors.toSet());
            // 注意：三元里两个分支类型必须一致（int vs Long 会编译不过）
            long interviewCount = ids.isEmpty() ? 0L : interviewMapper.selectCount(
                    new LambdaQueryWrapper<RecruitInterview>().in(RecruitInterview::getCandidateId, ids));
            stat.setInterviewCount((int) interviewCount);
            stat.setHireCount((int) list.stream()
                    .filter(c -> Objects.equals(c.getStage(), RecruitConstants.STAGE_HIRED)).count());
            stat.setInterviewRate(stat.getResumeCount() == 0 ? 0
                    : stat.getInterviewCount() * 100 / stat.getResumeCount());
            stat.setHireRate(stat.getResumeCount() == 0 ? 0
                    : stat.getHireCount() * 100 / stat.getResumeCount());
            channels.add(stat);
        }
        vo.setChannelStats(channels);

        // 淘汰原因分布
        Map<Integer, Integer> rejectMap = new LinkedHashMap<>();
        for (int t = 1; t <= 6; t++) {
            // ⚠️ 循环变量 t 不是 effectively final，lambda 里不能直接捕获 → 先复制一份
            final int rejectType = t;
            rejectMap.put(t, (int) candidates.stream()
                    .filter(c -> Objects.equals(c.getStage(), RecruitConstants.STAGE_REJECTED)
                            && Objects.equals(c.getRejectType(), rejectType)).count());
        }
        List<RecruitBoardVO.RejectStat> rejects = new ArrayList<>();
        rejectMap.forEach((type, count) -> {
            RecruitBoardVO.RejectStat stat = new RecruitBoardVO.RejectStat();
            stat.setRejectType(type);
            stat.setLabel(RecruitConstants.REJECT_LABEL.get(type));
            stat.setCount(count);
            rejects.add(stat);
        });
        vo.setRejectStats(rejects);

        // 部门维度
        Map<Long, String> deptNames = departmentMapper.selectList(
                        new LambdaQueryWrapper<Department>().select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        Map<Long, List<RecruitDemand>> byDept = demands.stream()
                .collect(Collectors.groupingBy(RecruitDemand::getDeptId));
        List<RecruitBoardVO.DeptStat> deptStats = new ArrayList<>();
        byDept.forEach((did, list) -> {
            RecruitBoardVO.DeptStat stat = new RecruitBoardVO.DeptStat();
            stat.setDeptId(did);
            stat.setDeptName(deptNames.getOrDefault(did, "未知部门"));
            stat.setDemandCount(list.size());
            stat.setHeadCount(list.stream().map(d -> d.getHeadCount() == null ? 0 : d.getHeadCount())
                    .reduce(0, Integer::sum));
            stat.setHiredCount(0);
            deptStats.add(stat);
        });
        deptStats.sort(Comparator.comparing(RecruitBoardVO.DeptStat::getHeadCount).reversed());
        vo.setDeptStats(deptStats);
        return vo;
    }
}
