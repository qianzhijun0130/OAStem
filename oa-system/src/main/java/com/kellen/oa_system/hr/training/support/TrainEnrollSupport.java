package com.kellen.oa_system.hr.training.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.training.entity.TrainAbsence;
import com.kellen.oa_system.hr.training.entity.TrainCourse;
import com.kellen.oa_system.hr.training.entity.TrainEnroll;
import com.kellen.oa_system.hr.training.entity.TrainSession;
import com.kellen.oa_system.hr.training.mapper.TrainAbsenceMapper;
import com.kellen.oa_system.hr.training.mapper.TrainCourseMapper;
import com.kellen.oa_system.hr.training.mapper.TrainEnrollMapper;
import com.kellen.oa_system.hr.training.mapper.TrainSessionMapper;
import com.kellen.oa_system.hr.training.vo.TrainEnrollVO;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报名 VO 组装（BE-OPT-08 拆分时抽出）。
 *
 * <p>一条报名要展示「期次 + 课程 + 用户 + 部门」四层信息，逐条查库就是 N+1，
 * 所以这里先批量取四张表再拼装。报名写入与报名查询两处都要用，故放在支撑包。</p>
 */
@Component
@RequiredArgsConstructor
public class TrainEnrollSupport {

    private final TrainSessionMapper sessionMapper;
    private final TrainCourseMapper courseMapper;
    private final TrainEnrollMapper enrollMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final TrainAbsenceMapper absenceMapper;

    public List<TrainEnrollVO> toVOs(List<TrainEnroll> enrolls) {
        if (enrolls.isEmpty()) {
            return List.of();
        }
        Set<Long> sessionIds = enrolls.stream().map(TrainEnroll::getSessionId).collect(Collectors.toSet());
        Map<Long, TrainSession> sessionMap = sessionIds.isEmpty() ? Map.of()
                : sessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(TrainSession::getId, s -> s, (a, b) -> a));
        Set<Long> courseIds = sessionMap.values().stream().map(TrainSession::getCourseId)
                .collect(Collectors.toSet());
        Map<Long, TrainCourse> courseMap = courseIds.isEmpty() ? Map.of()
                : courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(TrainCourse::getId, c -> c, (a, b) -> a));
        Set<Long> userIds = enrolls.stream().map(TrainEnroll::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Set<Long> deptIds = userMap.values().stream().map(User::getDeptId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : departmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));

        List<TrainEnrollVO> vos = new ArrayList<>(enrolls.size());
        for (TrainEnroll e : enrolls) {
            TrainEnrollVO vo = new TrainEnrollVO();
            vo.setId(e.getId());
            vo.setSessionId(e.getSessionId());
            TrainSession session = sessionMap.get(e.getSessionId());
            if (session != null) {
                vo.setSessionName(session.getSessionName());
                vo.setCourseId(session.getCourseId());
                TrainCourse course = courseMap.get(session.getCourseId());
                if (course != null) {
                    vo.setCourseName(course.getCourseName());
                    vo.setTrainType(course.getTrainType());
                    vo.setRequired(course.getRequired());
                    vo.setRequiredLabel(Objects.equals(course.getRequired(), 1) ? "必修" : "选修");
                    vo.setHours(course.getHours());
                }
            }
            vo.setUserId(e.getUserId());
            User user = userMap.get(e.getUserId());
            if (user != null) {
                vo.setUserName(user.getRealName());
                vo.setDeptName(deptNames.get(user.getDeptId()));
            }
            vo.setEnrollWay(e.getEnrollWay());
            vo.setEnrollWayLabel(TrainConstants.ENROLL_WAY_LABEL.getOrDefault(e.getEnrollWay(), "自助报名"));
            vo.setStatus(e.getStatus());
            vo.setStatusLabel(TrainConstants.ENROLL_STATUS_LABEL.getOrDefault(e.getStatus(), "未知"));
            vo.setAssignReason(e.getAssignReason());
            vo.setCheckinTime(e.getCheckinTime());
            vo.setRealHours(e.getRealHours());
            vo.setScore(e.getScore());
            vo.setAssessWay(e.getAssessWay());
            vo.setPassed(e.getPassed());
            vo.setSatisfied(e.getSatisfied());
            vo.setCertFileId(e.getCertFileId());
            vo.setCancelTime(e.getCancelTime());
            vo.setRemark(e.getRemark());
            vo.setCreateTime(e.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }

    /** 报名记录存在性校验 */
    public TrainEnroll requireEnroll(Long id) {
        TrainEnroll enroll = enrollMapper.selectById(id);
        if (enroll == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "报名记录不存在");
        }
        return enroll;
    }

    /**
     * 爽约限制截止日（未受限则 null）。
     *
     * <p>取「最近一条带限制的记录」的截止日——限制是滚动的，越近的记录越权威。</p>
     */
    public LocalDate restrictUntilOf(Long userId) {
        List<TrainAbsence> list = absenceMapper.selectList(new LambdaQueryWrapper<TrainAbsence>()
                .eq(TrainAbsence::getUserId, userId)
                .isNotNull(TrainAbsence::getRestrictedUntil)
                .orderByDesc(TrainAbsence::getAbsenceTime));
        return list.isEmpty() ? null : list.get(0).getRestrictedUntil();
    }

    /** 单条报名的 VO（报名成功后回显用） */
    public TrainEnrollVO toVO(TrainEnroll enroll) {
        return toVOs(List.of(enroll)).get(0);
    }
}
