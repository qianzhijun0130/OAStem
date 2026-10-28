package com.kellen.oa_system.hr.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.training.dto.TrainCourseSaveRequest;
import com.kellen.oa_system.hr.training.entity.TrainCourse;
import com.kellen.oa_system.hr.training.entity.TrainEnroll;
import com.kellen.oa_system.hr.training.entity.TrainSession;
import com.kellen.oa_system.hr.training.mapper.TrainCourseMapper;
import com.kellen.oa_system.hr.training.mapper.TrainEnrollMapper;
import com.kellen.oa_system.hr.training.mapper.TrainSessionMapper;
import com.kellen.oa_system.hr.training.support.TrainConstants;
import com.kellen.oa_system.hr.training.vo.TrainCourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 培训课程领域（BE-OPT-08 从 {@code TrainServiceImpl} 拆出）。
 *
 * <p>课程是培训模块的根：期次挂课程、报名挂期次。这里的「开课数 / 结业数」
 * 是列表页用的聚合列，需要跨到 session 与 enroll 两张表，因此本类同时注入两个 Mapper
 * ——但只读，不写它们。</p>
 *
 * <p><b>不含事务注解</b>：事务边界统一由其门面 {@link TrainServiceImpl} 持有，
 * 内层再标一次会因自调用/嵌套语义产生误导。</p>
 */
@Service
@RequiredArgsConstructor
public class TrainCourseService {

    private final TrainCourseMapper courseMapper;
    private final TrainSessionMapper sessionMapper;
    private final TrainEnrollMapper enrollMapper;

    /** 课程分页：关键词匹配编码或名称 */
    public PageResult<TrainCourseVO> pageCourses(String keyword, Integer type, Integer required,
                                                 Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<TrainCourse> wrapper = new LambdaQueryWrapper<TrainCourse>()
                .orderByDesc(TrainCourse::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(TrainCourse::getCourseCode, keyword)
                    .or().like(TrainCourse::getCourseName, keyword));
        }
        wrapper.eq(type != null, TrainCourse::getTrainType, type);
        wrapper.eq(required != null, TrainCourse::getRequired, required);
        Page<TrainCourse> page = courseMapper.selectPage(new Page<>(no, size), wrapper);
        return PageResult.of(toCourseVOs(page.getRecords()), page.getTotal(), (long) no, (long) size);
    }

    public TrainCourseVO courseDetail(Long id) {
        return toCourseVOs(List.of(requireCourse(id))).get(0);
    }

    /** 保存课程：编码唯一（修改时排除自身），新增走 insert、修改走 updateById */
    public Long saveCourse(TrainCourseSaveRequest request) {
        TrainCourse duplicate = courseMapper.selectOne(new LambdaQueryWrapper<TrainCourse>()
                .eq(TrainCourse::getCourseCode, request.getCourseCode()).last("LIMIT 1"));
        if (duplicate != null && !Objects.equals(duplicate.getId(), request.getId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课程编码已存在");
        }
        TrainCourse entity = request.getId() == null
                ? new TrainCourse() : courseMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.TRAIN_COURSE_INVALID);
        }
        entity.setCourseCode(request.getCourseCode());
        entity.setCourseName(request.getCourseName());
        entity.setTrainType(request.getTrainType() == null ? 1 : request.getTrainType());
        entity.setTeacherType(request.getTeacherType() == null ? 1 : request.getTeacherType());
        entity.setTeacherId(request.getTeacherId());
        entity.setTeacherName(request.getTeacherName());
        entity.setTeacherOrg(request.getTeacherOrg());
        entity.setHours(request.getHours());
        entity.setDeptIds(request.getDeptIds());
        entity.setSeries(request.getSeries());
        entity.setRequired(request.getRequired() == null ? 0 : request.getRequired());
        entity.setPassScore(request.getPassScore() == null ? new BigDecimal("60") : request.getPassScore());
        entity.setFileId(request.getFileId());
        entity.setCost(request.getCost() == null ? BigDecimal.ZERO : request.getCost());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            courseMapper.insert(entity);
        } else {
            courseMapper.updateById(entity);
        }
        return entity.getId();
    }

    /** 删除课程：已有期次则拒删（历史数据不能凭空消失，只能停用） */
    public void deleteCourse(Long id) {
        Long used = sessionMapper.selectCount(new LambdaQueryWrapper<TrainSession>()
                .eq(TrainSession::getCourseId, id));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该课程下已有期次，不能删除，可改为停用");
        }
        courseMapper.deleteById(id);
    }

    /** 课程存在性校验；期次域创建期次时也复用它 */
    public TrainCourse requireCourse(Long id) {
        TrainCourse course = courseMapper.selectById(id);
        if (course == null) {
            throw new BizException(ErrorCode.TRAIN_COURSE_INVALID);
        }
        return course;
    }

    private List<TrainCourseVO> toCourseVOs(List<TrainCourse> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }
        Set<Long> courseIds = courses.stream().map(TrainCourse::getId).collect(Collectors.toSet());
        Map<Long, Long> sessionCounts = sessionMapper.selectList(new LambdaQueryWrapper<TrainSession>()
                        .in(TrainSession::getCourseId, courseIds)).stream()
                .collect(Collectors.groupingBy(TrainSession::getCourseId, Collectors.counting()));
        List<TrainEnroll> allEnrolls = enrollMapper.selectList(new LambdaQueryWrapper<TrainEnroll>()
                .in(TrainEnroll::getSessionId, sessionMapper.selectList(
                                new LambdaQueryWrapper<TrainSession>().in(TrainSession::getCourseId, courseIds))
                        .stream().map(TrainSession::getId).toList()));
        Map<Long, Long> completedCounts = allEnrolls.stream()
                .filter(e -> Objects.equals(e.getStatus(), TrainConstants.ENROLL_COMPLETED))
                .collect(Collectors.groupingBy(e -> {
                    TrainSession s = sessionMapper.selectById(e.getSessionId());
                    return s == null ? 0L : s.getCourseId();
                }, Collectors.counting()));
        List<TrainCourseVO> vos = new ArrayList<>(courses.size());
        for (TrainCourse c : courses) {
            TrainCourseVO vo = new TrainCourseVO();
            vo.setId(c.getId());
            vo.setCourseCode(c.getCourseCode());
            vo.setCourseName(c.getCourseName());
            vo.setTrainType(c.getTrainType());
            vo.setTrainTypeLabel(TrainConstants.TRAIN_TYPE_LABEL.getOrDefault(c.getTrainType(), "通用素养"));
            vo.setTeacherType(c.getTeacherType());
            vo.setTeacherTypeLabel(Objects.equals(c.getTeacherType(), 2) ? "外部讲师" : "内部讲师");
            vo.setTeacherId(c.getTeacherId());
            vo.setTeacherName(c.getTeacherName());
            vo.setTeacherOrg(c.getTeacherOrg());
            vo.setHours(c.getHours());
            vo.setDeptIds(c.getDeptIds());
            vo.setSeries(c.getSeries());
            vo.setRequired(c.getRequired());
            vo.setRequiredLabel(Objects.equals(c.getRequired(), 1) ? "必修" : "选修");
            vo.setPassScore(c.getPassScore());
            vo.setFileId(c.getFileId());
            vo.setCost(c.getCost());
            vo.setStatus(c.getStatus());
            vo.setStatusLabel(Objects.equals(c.getStatus(), 2) ? "停用" : "启用");
            vo.setRemark(c.getRemark());
            vo.setSessionCount(sessionCounts.getOrDefault(c.getId(), 0L).intValue());
            vo.setCompletedCount(completedCounts.getOrDefault(c.getId(), 0L).intValue());
            vos.add(vo);
        }
        return vos;
    }
}
