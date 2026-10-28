package com.kellen.oa_system.hr.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.training.entity.TrainCourse;
import com.kellen.oa_system.hr.training.entity.TrainEnroll;
import com.kellen.oa_system.hr.training.entity.TrainSession;
import com.kellen.oa_system.hr.training.mapper.TrainCourseMapper;
import com.kellen.oa_system.hr.training.mapper.TrainEnrollMapper;
import com.kellen.oa_system.hr.training.mapper.TrainSessionMapper;
import com.kellen.oa_system.hr.training.support.TrainConstants;
import com.kellen.oa_system.hr.training.vo.TrainStatisticVO;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 培训统计（BE-OPT-08 从 {@code TrainServiceImpl} 拆出）。
 *
 * <p>口径要点（改之前先确认）：<b>学时按签到累加</b>（{@code real_hours}），报名不算；
 * 必修完成率的分母是「报了必修课的人次」而不是「在职人数」。</p>
 *
 * <p>实现上刻意一次取全量再在内存分组：年度范围内报名量在千级，比按课程/部门循环查库划算。</p>
 */
@Service
@RequiredArgsConstructor
public class TrainReportService {

    private final TrainEnrollMapper enrollMapper;
    private final TrainSessionMapper sessionMapper;
    private final TrainCourseMapper courseMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    public TrainStatisticVO statistics(String year) {
        TrainStatisticVO vo = new TrainStatisticVO();
        int yr = StringUtils.hasText(year) ? Integer.parseInt(year) : LocalDate.now().getYear();
        LocalDateTime from = LocalDateTime.of(yr, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(yr + 1, 1, 1, 0, 0);

        List<TrainEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .ge(TrainEnroll::getCreateTime, from)
                .lt(TrainEnroll::getCreateTime, to));
        List<TrainSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<TrainSession>()
                .ge(TrainSession::getStartTime, from)
                .lt(TrainSession::getStartTime, to));
        List<TrainCourse> courses = courseMapper.selectList(new LambdaQueryWrapper<TrainCourse>());
        Map<Long, TrainCourse> courseMap = courses.stream()
                .collect(Collectors.toMap(TrainCourse::getId, c -> c, (a, b) -> a));
        // 把课程ID塞进报名的临时字段，统计时按课程分组就不用反复查库
        Map<Long, TrainSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(TrainSession::getId, s -> s, (a, b) -> a));
        for (TrainEnroll e : enrolls) {
            TrainSession s = sessionMap.get(e.getSessionId());
            if (s != null) {
                e.setSessionCourseId(s.getCourseId());
            }
        }

        // 总学时与人均学时：按签到（real_hours）累加，报名不算
        BigDecimal totalHours = enrolls.stream()
                .map(e -> e.getRealHours() == null ? BigDecimal.ZERO : e.getRealHours())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Long staffCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getIsDeleted, 0)
                .in(User::getEmploymentStatus, List.of(2, 3, 4)));
        vo.setTotalHours(totalHours);
        vo.setStaffCount(staffCount == null ? 0 : staffCount.intValue());
        vo.setAvgHours(vo.getStaffCount() == 0 ? BigDecimal.ZERO
                : totalHours.divide(new BigDecimal(vo.getStaffCount()), 1, RoundingMode.HALF_UP));

        // 必修完成率
        long requiredEnroll = enrolls.stream()
                .filter(e -> {
                    TrainCourse c = courseMap.get(e.getSessionCourseId());
                    return c != null && Objects.equals(c.getRequired(), 1);
                }).count();
        long requiredDone = enrolls.stream()
                .filter(e -> {
                    TrainCourse c = courseMap.get(e.getSessionCourseId());
                    return c != null && Objects.equals(c.getRequired(), 1)
                            && Objects.equals(e.getStatus(), TrainConstants.ENROLL_COMPLETED);
                }).count();
        vo.setRequiredShouldCount((int) requiredEnroll);
        vo.setRequiredDoneCount((int) requiredDone);
        vo.setRequiredFinishRate(requiredEnroll == 0 ? 0 : (int) (requiredDone * 100 / requiredEnroll));

        // 满意度
        List<Integer> satisfied = enrolls.stream().map(TrainEnroll::getSatisfied)
                .filter(Objects::nonNull).toList();
        vo.setSatisfaction(satisfied.isEmpty() ? BigDecimal.ZERO
                : new BigDecimal(satisfied.stream().mapToInt(Integer::intValue).sum())
                .divide(new BigDecimal(satisfied.size()), 1, RoundingMode.HALF_UP));

        // 培训投入
        vo.setTotalCost(courses.stream()
                .map(c -> c.getCost() == null ? BigDecimal.ZERO : c.getCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 课程维度
        Map<Long, List<TrainEnroll>> byCourse = enrolls.stream()
                .filter(e -> e.getSessionCourseId() != null)
                .collect(Collectors.groupingBy(TrainEnroll::getSessionCourseId));
        List<TrainStatisticVO.CourseStat> courseStats = new ArrayList<>();
        byCourse.forEach((cid, list) -> {
            TrainStatisticVO.CourseStat stat = new TrainStatisticVO.CourseStat();
            stat.setCourseId(cid);
            TrainCourse c = courseMap.get(cid);
            stat.setCourseName(c == null ? "未知课程" : c.getCourseName());
            stat.setRequired(c == null ? 0 : c.getRequired());
            stat.setEnrollCount(list.size());
            stat.setCompletedCount((int) list.stream()
                    .filter(e -> Objects.equals(e.getStatus(), TrainConstants.ENROLL_COMPLETED)).count());
            long checked = list.stream().filter(e -> e.getCheckinTime() != null).count();
            stat.setAttendRate(list.isEmpty() ? 0 : (int) (checked * 100 / list.size()));
            BigDecimal hours = list.stream().map(e -> e.getRealHours() == null ? BigDecimal.ZERO : e.getRealHours())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            stat.setAvgHours(list.isEmpty() ? BigDecimal.ZERO
                    : hours.divide(new BigDecimal(list.size()), 1, RoundingMode.HALF_UP));
            List<Integer> sat = list.stream().map(TrainEnroll::getSatisfied).filter(Objects::nonNull).toList();
            stat.setSatisfaction(sat.isEmpty() ? BigDecimal.ZERO
                    : new BigDecimal(sat.stream().mapToInt(Integer::intValue).sum())
                    .divide(new BigDecimal(sat.size()), 1, RoundingMode.HALF_UP));
            courseStats.add(stat);
        });
        courseStats.sort(Comparator.comparing(TrainStatisticVO.CourseStat::getEnrollCount).reversed());
        vo.setCourseStats(courseStats);

        // 部门维度
        Map<Long, String> deptNames = departmentMapper.selectList(
                        new LambdaQueryWrapper<Department>().select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
        Map<Long, User> userMap = enrolls.stream().map(TrainEnroll::getUserId).filter(Objects::nonNull)
                .distinct().map(userMapper::selectById).filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, List<TrainEnroll>> byDept = new LinkedHashMap<>();
        for (TrainEnroll e : enrolls) {
            User u = userMap.get(e.getUserId());
            if (u == null || u.getDeptId() == null) {
                continue;
            }
            byDept.computeIfAbsent(u.getDeptId(), k -> new ArrayList<>()).add(e);
        }
        List<TrainStatisticVO.DeptStat> deptStats = new ArrayList<>();
        byDept.forEach((did, list) -> {
            TrainStatisticVO.DeptStat stat = new TrainStatisticVO.DeptStat();
            stat.setDeptId(did);
            stat.setDeptName(deptNames.getOrDefault(did, "未知部门"));
            stat.setEnrollCount(list.size());
            stat.setCompletedCount((int) list.stream()
                    .filter(e -> Objects.equals(e.getStatus(), TrainConstants.ENROLL_COMPLETED)).count());
            stat.setTotalHours(list.stream()
                    .map(e -> e.getRealHours() == null ? BigDecimal.ZERO : e.getRealHours())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            deptStats.add(stat);
        });
        deptStats.sort(Comparator.comparing(TrainStatisticVO.DeptStat::getTotalHours).reversed());
        vo.setDeptStats(deptStats);
        return vo;
    }
}
