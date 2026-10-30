package com.kellen.oa_system.hr.recruit.support;

import com.kellen.oa_system.hr.position.entity.Position;
import com.kellen.oa_system.hr.position.mapper.PositionMapper;
import com.kellen.oa_system.hr.recruit.entity.RecruitDemand;
import com.kellen.oa_system.hr.recruit.vo.RecruitDemandVO;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 招聘需求 VO 组装。
 *
 * <p>部门名、岗位名、HR 名各批量取一次；编制缺口例外——它按行调
 * {@link RecruitQuotaSupport}，属于已知的 N+1（见该类注释），本轮保持原行为。</p>
 */
@Component
@RequiredArgsConstructor
public class RecruitDemandAssembler {

    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final UserMapper userMapper;
    private final RecruitQuotaSupport quotaSupport;

    public List<RecruitDemandVO> toVOs(List<RecruitDemand> demands) {
        if (demands.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = demands.stream().map(RecruitDemand::getDeptId).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? new HashMap<>()
                : departmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        Set<Long> positionIds = demands.stream().map(RecruitDemand::getPositionId).collect(Collectors.toSet());
        Map<Long, String> positionNames = positionIds.isEmpty() ? new HashMap<>()
                : positionMapper.selectBatchIds(positionIds).stream()
                .collect(Collectors.toMap(Position::getId, Position::getPositionName, (a, b) -> a));
        Set<Long> hrIds = demands.stream().map(RecruitDemand::getHrId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> hrNames = hrIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(hrIds).stream()
                .collect(Collectors.toMap(User::getId, u -> Objects.requireNonNullElse(u.getRealName(), ""),
                        (a, b) -> a));

        List<RecruitDemandVO> vos = new ArrayList<>(demands.size());
        for (RecruitDemand d : demands) {
            RecruitDemandVO vo = new RecruitDemandVO();
            vo.setId(d.getId());
            vo.setDemandCode(d.getDemandCode());
            vo.setDeptId(d.getDeptId());
            vo.setDeptName(deptNames.get(d.getDeptId()));
            vo.setPositionId(d.getPositionId());
            vo.setPositionName(positionNames.get(d.getPositionId()));
            vo.setHeadCount(d.getHeadCount());
            vo.setUrgency(d.getUrgency());
            vo.setUrgencyLabel(RecruitConstants.URGENCY_LABEL.getOrDefault(d.getUrgency(), "普通"));
            vo.setExpectDate(d.getExpectDate());
            vo.setEducation(d.getEducation());
            vo.setExperience(d.getExperience());
            vo.setSkillRequire(d.getSkillRequire());
            vo.setSalaryMin(d.getSalaryMin());
            vo.setSalaryMax(d.getSalaryMax());
            vo.setReasonType(d.getReasonType());
            vo.setReasonTypeLabel(RecruitConstants.REASON_TYPE_LABEL.getOrDefault(d.getReasonType(), "新增"));
            vo.setReason(d.getReason());
            vo.setStatus(d.getStatus());
            vo.setStatusLabel(RecruitConstants.DEMAND_STATUS_LABEL.getOrDefault(d.getStatus(), "未知"));
            vo.setHrId(d.getHrId());
            vo.setHrName(hrNames.get(d.getHrId()));
            vo.setApproveTime(d.getApproveTime());
            vo.setFinishTime(d.getFinishTime());
            vo.setRemark(d.getRemark());
            vo.setCreateTime(d.getCreateTime());
            int[] quota = quotaSupport.quotaOf(d);
            vo.setQuotaTotal(quota[0]);
            vo.setQuotaUsed(quota[1]);
            vo.setQuotaGap(quota[2]);
            vo.setOverQuota(quotaSupport.isOverQuota(d) ? 1 : 0);
            if (d.getApproveTime() != null && d.getFinishTime() != null) {
                vo.setCycleDays((int) ChronoUnit.DAYS.between(
                        d.getApproveTime().toLocalDate(), d.getFinishTime().toLocalDate()));
            }
            vo.setHiredCount(0);
            vos.add(vo);
        }
        return vos;
    }
}
