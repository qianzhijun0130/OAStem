package com.kellen.oa_system.hr.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.attendance.dto.ShiftSaveRequest;
import com.kellen.oa_system.hr.attendance.entity.Shift;
import com.kellen.oa_system.hr.attendance.entity.ShiftSchedule;
import com.kellen.oa_system.hr.attendance.mapper.ShiftMapper;
import com.kellen.oa_system.hr.attendance.mapper.ShiftScheduleMapper;
import com.kellen.oa_system.hr.attendance.support.AttendanceRules;
import com.kellen.oa_system.hr.attendance.vo.ShiftVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 班次：考勤的最底层配置（上下班时间、弹性、免打卡）。
 *
 * <p>班次是「被引用的事实」：排班指向它、历史考勤也指向它。所以删除前必须查引用，
 * 只允许停用——停用不影响历史重算，删除会让历史排班指向一个不存在的班次。</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceShiftService {

    private final ShiftMapper shiftMapper;
    private final ShiftScheduleMapper scheduleMapper;

    public List<ShiftVO> listShifts() {
        List<Shift> list = shiftMapper.selectList(new LambdaQueryWrapper<Shift>()
                .orderByAsc(Shift::getId));
        List<ShiftVO> vos = new ArrayList<>(list.size());
        for (Shift s : list) {
            ShiftVO vo = new ShiftVO();
            vo.setId(s.getId());
            vo.setShiftCode(s.getShiftCode());
            vo.setShiftName(s.getShiftName());
            vo.setWorkStart(s.getWorkStart());
            vo.setWorkEnd(s.getWorkEnd());
            vo.setRestMinutes(s.getRestMinutes());
            vo.setLateGrace(s.getLateGrace());
            vo.setEarlyGrace(s.getEarlyGrace());
            vo.setFlexible(s.getFlexible());
            vo.setFreeClock(s.getFreeClock());
            vo.setStatus(s.getStatus());
            vo.setStandardHours(AttendanceRules.standardHours(s));
            vos.add(vo);
        }
        return vos;
    }

    public Long saveShift(ShiftSaveRequest request) {
        LambdaQueryWrapper<Shift> dup = new LambdaQueryWrapper<Shift>()
                .eq(Shift::getShiftCode, request.getShiftCode().trim());
        if (request.getId() != null) {
            dup.ne(Shift::getId, request.getId());
        }
        if (shiftMapper.selectCount(dup) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "班次编码已存在");
        }
        Shift entity = request.getId() == null ? new Shift() : shiftMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "班次不存在");
        }
        entity.setShiftCode(request.getShiftCode().trim());
        entity.setShiftName(request.getShiftName().trim());
        entity.setWorkStart(request.getWorkStart());
        entity.setWorkEnd(request.getWorkEnd());
        entity.setRestMinutes(request.getRestMinutes() == null ? 60 : request.getRestMinutes());
        entity.setLateGrace(request.getLateGrace() == null ? 5 : request.getLateGrace());
        entity.setEarlyGrace(request.getEarlyGrace() == null ? 0 : request.getEarlyGrace());
        entity.setFlexible(request.getFlexible() == null ? 0 : request.getFlexible());
        entity.setFreeClock(request.getFreeClock() == null ? 0 : request.getFreeClock());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        if (request.getId() == null) {
            shiftMapper.insert(entity);
        } else {
            shiftMapper.updateById(entity);
        }
        return entity.getId();
    }

    public void deleteShift(Long id) {
        Shift shift = shiftMapper.selectById(id);
        if (shift == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "班次不存在");
        }
        // 已排班的班次不允许删除：删了会让历史排班指向不存在的班次，考勤无法重算
        Long used = scheduleMapper.selectCount(new LambdaQueryWrapper<ShiftSchedule>()
                .eq(ShiftSchedule::getShiftId, id));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "该班次已被 " + used + " 条排班引用，请先停用而不是删除");
        }
        shiftMapper.deleteById(id);
    }

    public void updateShiftStatus(Long id, Integer status) {
        Shift shift = shiftMapper.selectById(id);
        if (shift == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "班次不存在");
        }
        Shift patch = new Shift();
        patch.setId(id);
        patch.setStatus(status);
        shiftMapper.updateById(patch);
    }
}
