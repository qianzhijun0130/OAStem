package com.kellen.oa_system.hr.attendance.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.attendance.entity.AttendanceMonth;
import com.kellen.oa_system.hr.attendance.entity.Shift;
import com.kellen.oa_system.hr.attendance.mapper.ShiftMapper;
import com.kellen.oa_system.hr.attendance.vo.AttendanceMonthVO;
import com.kellen.oa_system.hr.attendance.vo.AttendanceVO;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考勤 VO 组装（日明细 / 月汇总）。
 *
 * <p>用户、部门、班次一律<b>按批取一次</b>：分页 20 行逐个查会变成 60 次单行查询，
 * 而这三张表的数据量都只有几百行到几千行。</p>
 */
@Component
@RequiredArgsConstructor
public class AttendanceVoAssembler {

    /** 仅用于把月汇总里的 JSON 明细转成展示文本，与业务序列化无关 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final ShiftMapper shiftMapper;

    public List<AttendanceVO> toAttendanceVOs(List<Attendance> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = loadUsers(list.stream().map(Attendance::getUserId).collect(Collectors.toSet()));
        Map<Long, String> deptNames = loadDeptNames();
        Map<Long, String> shiftNames = shiftMapper.selectList(null).stream()
                .collect(Collectors.toMap(Shift::getId, Shift::getShiftName, (a, b) -> a));

        List<AttendanceVO> vos = new ArrayList<>(list.size());
        for (Attendance a : list) {
            AttendanceVO vo = new AttendanceVO();
            vo.setId(a.getId());
            vo.setUserId(a.getUserId());
            fillUser(vo, users.get(a.getUserId()), deptNames);
            vo.setAttendanceDate(a.getAttendanceDate());
            vo.setCheckInTime(a.getCheckInTime());
            vo.setCheckOutTime(a.getCheckOutTime());
            // 来源要发给前端：网页打卡 / 考勤机 / 补卡 三种数据可信度不同，
            // HR 判断一条异常记录时，第一件事就是看它是怎么来的
            vo.setCheckInSource(a.getCheckInSource());
            vo.setCheckOutSource(a.getCheckOutSource());
            vo.setCheckInLocation(a.getCheckInLocation());
            vo.setCheckOutLocation(a.getCheckOutLocation());
            vo.setWorkDuration(a.getWorkDuration());
            vo.setAttendanceStatus(a.getAttendanceStatus());
            vo.setAttendanceStatusLabel(AttendanceRules.statusLabel(a.getAttendanceStatus()));
            vo.setLateMinutes(a.getLateMinutes());
            vo.setEarlyMinutes(a.getEarlyMinutes());
            vo.setCardMissing(a.getCardMissing());
            vo.setShiftName(shiftNames.get(a.getShiftId()));
            vo.setSettled(a.getSettled());
            vo.setRemark(a.getRemark());
            vos.add(vo);
        }
        return vos;
    }

    public List<AttendanceMonthVO> toMonthVOs(List<AttendanceMonth> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = loadUsers(list.stream()
                .map(AttendanceMonth::getUserId).collect(Collectors.toSet()));
        Map<Long, String> deptNames = loadDeptNames();
        List<AttendanceMonthVO> vos = new ArrayList<>(list.size());
        for (AttendanceMonth m : list) {
            AttendanceMonthVO vo = new AttendanceMonthVO();
            vo.setId(m.getId());
            vo.setUserId(m.getUserId());
            User u = users.get(m.getUserId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setWorkNumber(u.getWorkNumber());
                vo.setDeptId(u.getDeptId());
                vo.setDeptName(deptNames.get(u.getDeptId()));
            }
            vo.setMonth(m.getMonth());
            vo.setShouldDays(m.getShouldDays());
            vo.setActualDays(m.getActualDays());
            vo.setLateCount(m.getLateCount());
            vo.setEarlyCount(m.getEarlyCount());
            vo.setAbsentDays(m.getAbsentDays());
            vo.setMissingCount(m.getMissingCount());
            vo.setTripDays(m.getTripDays());
            vo.setLeaveDaysJson(m.getLeaveDaysJson());
            vo.setLeaveDaysText(readable(m.getLeaveDaysJson(), "天"));
            vo.setOvertimeJson(m.getOvertimeJson());
            vo.setOvertimeText(readable(m.getOvertimeJson(), "小时"));
            vo.setStatus(m.getStatus());
            vo.setLockTime(m.getLockTime());
            vos.add(vo);
        }
        return vos;
    }

    private Map<Long, User> loadUsers(java.util.Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private Map<Long, String> loadDeptNames() {
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .select(Department::getId, Department::getDeptName))
                .stream().collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));
    }

    private void fillUser(AttendanceVO vo, User u, Map<Long, String> deptNames) {
        if (u == null) {
            return;
        }
        vo.setRealName(u.getRealName());
        vo.setWorkNumber(u.getWorkNumber());
        vo.setDeptId(u.getDeptId());
        vo.setDeptName(deptNames.get(u.getDeptId()));
    }

    /**
     * 把 {"年假":2.0,"病假":1.0} 转成「年假 2 天 · 病假 1 天」。
     *
     * <p>解析失败时原样返回——展示层不能因为一条脏数据把整个列表搞崩，
     * 让运维看到原始 JSON 反而更容易定位问题。</p>
     */
    static String readable(String json, String unit) {
        if (!StringUtils.hasText(json)) {
            return "";
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            if (map == null || map.isEmpty()) {
                return "";
            }
            List<String> parts = new ArrayList<>();
            map.forEach((k, v) -> parts.add(k + " " + stripZero(v) + " " + unit));
            return String.join(" · ", parts);
        } catch (RuntimeException e) {
            return json;
        }
    }

    private static String stripZero(Object value) {
        if (value == null) {
            return "0";
        }
        BigDecimal decimal = new BigDecimal(String.valueOf(value)).stripTrailingZeros();
        return decimal.scale() <= 0 ? decimal.toBigInteger().toString() : decimal.toPlainString();
    }
}
