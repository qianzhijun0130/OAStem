package com.kellen.oa_system.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.common.enums.DataScopeEnum;
import com.kellen.oa_system.system.dto.DeptSaveRequest;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.DeptService;
import com.kellen.oa_system.system.service.WorkNumberService;
import com.kellen.oa_system.system.service.support.DataScopeSupport;
import com.kellen.oa_system.system.vo.DeptVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 部门业务实现。
 *
 * <p>部门量级通常在数百以内，树形结构采用「一次查全表 + 内存构树」，
 * 避免递归查库造成的 N+1 问题；负责人姓名同样是一次批量查询后映射，不做逐条关联。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    /** 向上追溯父级时的最大层级，防御脏数据造成的死循环 */
    private static final int MAX_PARENT_DEPTH = 32;

    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final WorkNumberService workNumberService;
    private final DataScopeSupport dataScopeSupport;

    @Override
    @DataScope
    public List<DeptVO> getDeptTree(String deptName, Integer status) {
        List<Department> all = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>()
                        .orderByAsc(Department::getSort)
                        .orderByAsc(Department::getId));

        if (all.isEmpty()) {
            return List.of();
        }

        // 数据权限：非「全部数据」时，只保留「本部门 + 其全部下级部门」。
        // 部门表自身没有 dept_id 列，故不能套用 DataScopeSupport#applyTo 的通用签名。
        all = filterByDataScope(all);

        if (all.isEmpty()) {
            return List.of();
        }

        boolean filtered = StringUtils.hasText(deptName) || status != null;
        List<Department> effective = filtered ? filterWithAncestors(all, deptName, status) : all;

        return buildTree(effective);
    }

    /**
     * 按当前登录用户的数据范围裁剪部门集合。
     *
     * <p>口径：{@code ALL} 全部放行；{@code DEPT} 保留本部门及其<b>所有下级</b>
     * （部门是树形，管理��看到子树才有意义）；{@code SELF} 收敛为仅本部门。</p>
     *
     * <p>用户无归属部门时返回空集合——数据权限属安全边界，宁可少给也不误放开。</p>
     */
    private List<Department> filterByDataScope(List<Department> all) {
        DataScopeSupport.DataScopeSnapshot snapshot = dataScopeSupport.currentSnapshot();
        if (snapshot == null || snapshot.scope() == DataScopeEnum.ALL) {
            return all;
        }
        Long root = snapshot.deptId();
        if (root == null) {
            log.debug("数据范围受限但用户无部门，部门树收敛为不可见: userId={}", snapshot.userId());
            return List.of();
        }
        Set<Long> visible = collectSubtreeIds(all, root);
        return all.stream().filter(d -> visible.contains(d.getId())).collect(Collectors.toList());
    }

    /**
     * 自底向上收集以 {@code rootId} 为根的子树 id 集合。
     *
     * <p>部门量级小（通常数百），不做递归查库，直接在内存中逐轮收敛。
     * 轮数上限由「集合不再增长」决定，脏数据造成的环不会导致死循环。</p>
     */
    private Set<Long> collectSubtreeIds(List<Department> all, Long rootId) {
        Set<Long> visible = new HashSet<>();
        visible.add(rootId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Department d : all) {
                Long parentId = d.getParentId();
                if (parentId != null && visible.contains(parentId) && visible.add(d.getId())) {
                    changed = true;
                }
            }
        }
        return visible;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDept(DeptSaveRequest request) {
        Long parentId = request.getParentId() == null ? 0L : request.getParentId();
        Long companyId = request.getCompanyId();

        if (parentId > 0) {
            Department parent = departmentMapper.selectById(parentId);
            if (parent == null) {
                throw new BizException(ErrorCode.DEPT_NOT_FOUND);
            }
            if (companyId == null) {
                companyId = parent.getCompanyId();
            }
        }
        if (companyId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "顶级部门必须指定所属公司");
        }

        assertDeptCodeUnique(request.getDeptCode(), request.getId());

        Department dept = new Department();
        dept.setDeptName(request.getDeptName().trim());
        dept.setDeptCode(request.getDeptCode().trim());
        dept.setWorkNoPrefix(resolveWorkNoPrefix(request.getWorkNoPrefix(), request.getId(),
                request.getDeptName().trim()));
        dept.setCompanyId(companyId);
        dept.setParentId(parentId);
        dept.setLeaderId(request.getLeaderId());
        dept.setDeptType(request.getDeptType() == null ? 1 : request.getDeptType());
        dept.setSort(request.getSort() == null ? 0 : request.getSort());
        dept.setStatus(request.getStatus() == null ? 1 : request.getStatus());

        if (request.getId() == null) {
            departmentMapper.insert(dept);
            log.info("新增部门: id={}, name={}", dept.getId(), dept.getDeptName());
            return dept.getId();
        }

        Department existing = departmentMapper.selectById(request.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND);
        }
        // 把父级指向自身或其后代会形成环，必须拦截
        if (parentId > 0 && isSelfOrDescendant(parentId, existing.getId())) {
            throw new BizException(ErrorCode.DEPT_PARENT_INVALID);
        }
        dept.setId(request.getId());
        departmentMapper.updateById(dept);
        log.info("修改部门: id={}, name={}", dept.getId(), dept.getDeptName());
        return dept.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(Long id) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND);
        }
        Long childCount = departmentMapper.selectCount(
                new LambdaQueryWrapper<Department>().eq(Department::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(ErrorCode.DEPT_HAS_CHILDREN);
        }
        Long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getDeptId, id));
        if (userCount != null && userCount > 0) {
            throw new BizException(ErrorCode.DEPT_HAS_USER);
        }
        // 唯一性已由数据库保证：uk_dept_code 改为 (dept_code, active_flag)，
        // 已删行 active_flag=NULL，原编码自动释放，应用层无需改写业务值
        departmentMapper.deleteById(id);
        log.info("删除部门: id={}, name={}", id, dept.getDeptName());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND);
        }
        Department update = new Department();
        update.setId(id);
        update.setStatus(status);
        departmentMapper.updateById(update);
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    /**
     * 解析本次保存应落库的工号前缀。
     *
     * <p>优先级：① 管理员显式填写 → 大写化并校验未被其他部门占用；
     * ② 未填写且部门已有前缀 → 保留原值，避免「改了部门名顺带把历史工号前缀洗掉」；
     * ③ 未填写且部门尚无前缀（新增，或历史数据未分配）→ 按部门名称自动推导。</p>
     */
    private String resolveWorkNoPrefix(String requestedPrefix, Long deptId, String deptName) {
        if (StringUtils.hasText(requestedPrefix)) {
            String prefix = requestedPrefix.trim().toUpperCase(Locale.ROOT);
            workNumberService.assertPrefixAvailable(prefix, deptId);
            return prefix;
        }
        Department existing = deptId == null ? null : departmentMapper.selectById(deptId);
        if (existing != null && StringUtils.hasText(existing.getWorkNoPrefix())) {
            return existing.getWorkNoPrefix();
        }
        return workNumberService.derivePrefix(deptName, deptId);
    }

    private void assertDeptCodeUnique(String deptCode, Long excludeId) {
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
                .eq(Department::getDeptCode, deptCode.trim());
        if (excludeId != null) {
            wrapper.ne(Department::getId, excludeId);
        }
        Long count = departmentMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.DEPT_CODE_DUPLICATE);
        }
    }

    /**
     * 判断 candidateId 是否为 selfId 自身或其后代，用于阻止把父级指向自己的子树。
     *
     * <p>沿 {@code parentId} 走完整条祖先链，以 visited 集合防环（既存脏数据已成环时提前终止），
     * 不设层级上限——原 32 层上限会让超长链的后代漏判，进而放行成环。</p>
     */
    private boolean isSelfOrDescendant(Long candidateId, Long selfId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = candidateId;
        while (cursor != null && cursor > 0) {
            if (Objects.equals(cursor, selfId)) {
                return true;
            }
            if (!visited.add(cursor)) {
                // 祖先链出现重复节点 = 既存数据已成环且环内不含 selfId，继续追溯无意义
                return false;
            }
            Department current = departmentMapper.selectById(cursor);
            cursor = current == null ? null : current.getParentId();
        }
        return false;
    }

    /**
     * 按条件过滤并保留命中节点的祖先链，保证树结构完整。
     */
    private List<Department> filterWithAncestors(List<Department> all, String deptName, Integer status) {
        Map<Long, Department> byId = all.stream()
                .collect(Collectors.toMap(Department::getId, Function.identity(), (a, b) -> a, HashMap::new));

        Set<Long> keep = new HashSet<>();
        for (Department dept : all) {
            if (!matches(dept, deptName, status)) {
                continue;
            }
            Long cursor = dept.getId();
            for (int i = 0; i < MAX_PARENT_DEPTH && cursor != null && cursor > 0; i++) {
                // add 返回 false 说明该节点已在集合中，停止避免重复或成环
                if (!keep.add(cursor)) {
                    break;
                }
                Department parent = byId.get(cursor);
                cursor = parent == null ? null : parent.getParentId();
            }
        }

        return all.stream().filter(d -> keep.contains(d.getId())).collect(Collectors.toList());
    }

    private boolean matches(Department dept, String deptName, Integer status) {
        if (status != null && !status.equals(dept.getStatus())) {
            return false;
        }
        if (StringUtils.hasText(deptName)
                && !dept.getDeptName().toLowerCase().contains(deptName.toLowerCase())) {
            return false;
        }
        return true;
    }

    /**
     * 内存构树：先建索引，再按 parentId 挂载；父节点不在本次数据集内的视为根节点。
     */
    private List<DeptVO> buildTree(List<Department> list) {
        Map<Long, String> leaderNames = resolveLeaderNames(list);

        Map<Long, DeptVO> nodeMap = new HashMap<>(list.size() * 2);
        List<DeptVO> nodes = new ArrayList<>(list.size());
        for (Department dept : list) {
            DeptVO vo = DeptVO.builder()
                    .id(dept.getId())
                    .parentId(dept.getParentId() == null ? 0L : dept.getParentId())
                    .deptName(dept.getDeptName())
                    .deptCode(dept.getDeptCode())
                    .workNoPrefix(dept.getWorkNoPrefix())
                    .companyId(dept.getCompanyId())
                    .leaderId(dept.getLeaderId())
                    .leaderName(dept.getLeaderId() == null ? null : leaderNames.get(dept.getLeaderId()))
                    .deptType(dept.getDeptType())
                    .sort(dept.getSort())
                    .status(dept.getStatus())
                    .createTime(dept.getCreateTime())
                    .children(new ArrayList<>())
                    .build();
            nodeMap.put(vo.getId(), vo);
            nodes.add(vo);
        }

        List<DeptVO> roots = new ArrayList<>();
        for (DeptVO node : nodes) {
            DeptVO parent = nodeMap.get(node.getParentId());
            if (parent != null && !Objects.equals(parent.getId(), node.getId())) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        sortRecursively(roots);
        return roots;
    }

    private void sortRecursively(List<DeptVO> nodes) {
        nodes.sort(Comparator.comparing(DeptVO::getSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(DeptVO::getId, Comparator.nullsLast(Long::compareTo)));
        for (DeptVO node : nodes) {
            if (!node.getChildren().isEmpty()) {
                sortRecursively(node.getChildren());
            }
        }
    }

    /**
     * 批量查询负责人姓名，避免逐条查库。
     */
    private Map<Long, String> resolveLeaderNames(List<Department> list) {
        Set<Long> leaderIds = list.stream()
                .map(Department::getLeaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (leaderIds.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectBatchIds(leaderIds);
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        return users.stream().collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
    }
}
