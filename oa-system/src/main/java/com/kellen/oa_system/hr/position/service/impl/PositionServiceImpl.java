package com.kellen.oa_system.hr.position.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.hr.position.dto.PositionPageQuery;
import com.kellen.oa_system.hr.position.dto.PositionSaveRequest;
import com.kellen.oa_system.hr.position.entity.Position;
import com.kellen.oa_system.hr.position.mapper.PositionDutyCountRow;
import com.kellen.oa_system.hr.position.mapper.PositionMapper;
import com.kellen.oa_system.hr.position.service.PositionService;
import com.kellen.oa_system.hr.position.vo.PositionVO;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 岗位业务实现。
 *
 * <p>列表页的「部门名称」与「在岗人数」两列都是批量补全（各 1 条 SQL），
 * 不做逐行关联，避免 10 行数据产生 20 次查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    /** 在岗口径：试用 / 正式 / 待离职 */
    private static final List<Integer> ON_DUTY_STATUS = List.of(2, 3, 4);

    /** 占用岗位的口径（删除校验）：待入职也算，否则删了岗位会让待入职员工悬空 */
    private static final List<Integer> OCCUPIED_STATUS = List.of(1, 2, 3, 4);

    private final PositionMapper positionMapper;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;

    @Override
    public PageResult<PositionVO> page(PositionPageQuery query) {
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        LambdaQueryWrapper<Position> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(Position::getPositionCode, kw)
                    .or().like(Position::getPositionName, kw));
        }
        if (query.getDeptId() != null) {
            wrapper.eq(Position::getDeptId, query.getDeptId());
        }
        if (StringUtils.hasText(query.getSeries())) {
            wrapper.eq(Position::getSeries, query.getSeries().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Position::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(Position::getSort).orderByAsc(Position::getId);

        Page<Position> page = positionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<PositionVO> listOptions(Long deptId) {
        LambdaQueryWrapper<Position> wrapper = new LambdaQueryWrapper<Position>()
                .eq(Position::getStatus, 1)
                .orderByAsc(Position::getSort)
                .orderByAsc(Position::getId);
        if (deptId != null) {
            wrapper.eq(Position::getDeptId, deptId);
        }
        return toVOs(positionMapper.selectList(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PositionSaveRequest request) {
        Department dept = departmentMapper.selectById(request.getDeptId());
        if (dept == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND);
        }

        // 岗位编码同公司内唯一（逻辑删后允许复用，靠 active_flag 生成列让位）
        LambdaQueryWrapper<Position> dup = new LambdaQueryWrapper<Position>()
                .eq(Position::getCompanyId, dept.getCompanyId())
                .eq(Position::getPositionCode, request.getPositionCode().trim());
        if (request.getId() != null) {
            dup.ne(Position::getId, request.getId());
        }
        if (positionMapper.selectCount(dup) > 0) {
            throw new BizException(ErrorCode.HR_POSITION_CODE_DUPLICATE);
        }

        Position entity;
        if (request.getId() == null) {
            entity = new Position();
        } else {
            entity = positionMapper.selectById(request.getId());
            if (entity == null) {
                throw new BizException(ErrorCode.HR_POSITION_NOT_FOUND);
            }
        }
        entity.setPositionCode(request.getPositionCode().trim());
        entity.setPositionName(request.getPositionName().trim());
        entity.setCompanyId(dept.getCompanyId());
        entity.setDeptId(dept.getId());
        entity.setSeries(StringUtils.hasText(request.getSeries()) ? request.getSeries() : "P");
        entity.setLevelCode(request.getLevelCode());
        entity.setHeadcount(request.getHeadcount() == null ? 0 : request.getHeadcount());
        entity.setDescription(request.getDescription());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setSort(request.getSort() == null ? 0 : request.getSort());

        if (request.getId() == null) {
            positionMapper.insert(entity);
        } else {
            positionMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Position position = positionMapper.selectById(id);
        if (position == null) {
            throw new BizException(ErrorCode.HR_POSITION_NOT_FOUND);
        }
        Long occupied = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPositionId, id)
                .in(User::getEmploymentStatus, OCCUPIED_STATUS));
        if (occupied != null && occupied > 0) {
            throw new BizException(ErrorCode.HR_POSITION_IN_USE,
                    "该岗位下有 " + occupied + " 名员工（含待入职），请先调岗后再删除");
        }
        positionMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Position position = positionMapper.selectById(id);
        if (position == null) {
            throw new BizException(ErrorCode.HR_POSITION_NOT_FOUND);
        }
        // 只提交 id + status：部分实体更新，不会把其余字段覆盖为 null
        Position patch = new Position();
        patch.setId(id);
        patch.setStatus(status);
        positionMapper.updateById(patch);
    }

    /**
     * 实体转 VO：部门名称与在岗人数批量补全。
     */
    private List<PositionVO> toVOs(List<Position> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = list.stream().map(Position::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty()
                ? Map.of()
                : departmentMapper.selectBatchIds(deptIds).stream()
                        .collect(Collectors.toMap(Department::getId, Department::getDeptName, (a, b) -> a));

        Map<Long, Long> onDuty = new HashMap<>();
        List<Long> ids = list.stream().map(Position::getId).collect(Collectors.toList());
        for (PositionDutyCountRow row : positionMapper.countOnDuty(ids)) {
            onDuty.put(row.getPositionId(), row.getOnDutyCount());
        }

        List<PositionVO> vos = new ArrayList<>(list.size());
        for (Position p : list) {
            PositionVO vo = new PositionVO();
            vo.setId(p.getId());
            vo.setPositionCode(p.getPositionCode());
            vo.setPositionName(p.getPositionName());
            vo.setCompanyId(p.getCompanyId());
            vo.setDeptId(p.getDeptId());
            vo.setDeptName(deptNames.get(p.getDeptId()));
            vo.setSeries(p.getSeries());
            vo.setLevelCode(p.getLevelCode());
            vo.setHeadcount(p.getHeadcount());
            long count = onDuty.getOrDefault(p.getId(), 0L);
            vo.setOnDutyCount((int) count);
            vo.setOverHeadcount(p.getHeadcount() != null && p.getHeadcount() > 0 && count > p.getHeadcount());
            vo.setDescription(p.getDescription());
            vo.setStatus(p.getStatus());
            vo.setSort(p.getSort());
            vo.setCreateTime(p.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }
}
