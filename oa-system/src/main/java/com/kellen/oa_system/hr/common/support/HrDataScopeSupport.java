package com.kellen.oa_system.hr.common.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.enums.DataScopeEnum;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.service.support.DataScopeSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 人力资源模块的数据范围拼装。
 *
 * <p>为什么不直接用 {@link DataScopeSupport#applyTo}：通用实现把「本部门」表达为
 * {@code dept_id = 当前部门}，不含下级；而 HR 的花名册、考勤、假勤都必须能看到
 * 「本部门 + 全部下级」（主管看团队、HR 看全员），否则主管只能看到与自己同部门的人。
 * 该口径已在《开发文档/人力资源/06》第七节固化。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HrDataScopeSupport {

    private final DataScopeSupport dataScopeSupport;
    private final DepartmentMapper departmentMapper;

    /**
     * 当前是否「不受数据范围限制」（未被 {@code @DataScope} 标注，或范围=全部）。
     *
     * <p>供需要把范围转成「用户ID集合」的查询使用：不受限时应直接跳过 IN 条件，
     * 而不是把全站用户 ID 拼进 SQL。</p>
     */
    public boolean isUnrestricted() {
        DataScopeSupport.DataScopeSnapshot snapshot = dataScopeSupport.currentSnapshot();
        return snapshot == null || snapshot.scope() == DataScopeEnum.ALL;
    }

    /**
     * 把「本部门 + 下级 / 仅本人 / 全部」条件拼进用户查询。
     *
     * <p>未被 {@code @DataScope} 标注时（快照为 null）不施加任何限制——
     * 数据权限是收窄可见范围的增强，不应让未接入的查询不可用。</p>
     */
    public void applyToUser(LambdaQueryWrapper<User> wrapper) {
        DataScopeSupport.DataScopeSnapshot snapshot = dataScopeSupport.currentSnapshot();
        if (snapshot == null || snapshot.scope() == DataScopeEnum.ALL) {
            return;
        }
        if (snapshot.scope() == DataScopeEnum.SELF) {
            wrapper.eq(User::getId, snapshot.userId());
            return;
        }
        Long deptId = snapshot.deptId();
        if (deptId == null) {
            log.debug("数据范围为本部门但用户无部门，收敛为不可见: userId={}", snapshot.userId());
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(User::getDeptId, subtreeIds(deptId));
    }

    /**
     * 收集以 {@code rootId} 为根的部门子树 ID（含自身）。
     *
     * <p>部门量级通常在数百以内，一次查全表后内存收敛，避免递归查库；
     * 轮次由「集合不再增长」决定，脏数据形成的环不会死循环。</p>
     */
    public Set<Long> subtreeIds(Long rootId) {
        List<Department> all = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .select(Department::getId, Department::getParentId));
        Set<Long> ids = new HashSet<>();
        ids.add(rootId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Department d : all) {
                if (d.getParentId() != null && ids.contains(d.getParentId()) && ids.add(d.getId())) {
                    changed = true;
                }
            }
        }
        return ids;
    }
}
