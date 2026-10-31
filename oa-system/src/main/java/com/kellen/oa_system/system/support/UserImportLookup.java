package com.kellen.oa_system.system.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.Role;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.RoleMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 导入前一次性预取的索引与占用集合。
 *
 * <p>为什么必须预取：旧实现对每一行都单独查一次「账号/手机号/工号是否已存在」，
 * 5000 行就是 15000 次往返；而且「文件内重复」与「库内重复」两套判断混在一起，
 * 出错时看不出到底是哪种。现在改成导入开始前各查一次，之后全是内存比较。</p>
 *
 * <p>占用集合的口径：</p>
 * <ul>
 *   <li>账号、手机号、身份证<b>只看存活档案</b>（逻辑删除的不算占用）；</li>
 *   <li>工号<b>含已删除</b>——业务约定工号不回收、不复用，与
 *       {@code UserMapper#countByWorkNumberIncludingDeleted} 保持一致。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class UserImportLookup {

    private final DepartmentMapper departmentMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;

    /** 部门索引：名称与编码都可作键 */
    private Map<String, Department> deptIndex = Map.of();

    /** 角色索引：名称与编码都可作键 */
    private Map<String, Role> roleIndex = Map.of();

    /** 工号（小写）→ 用户，用于解析直属上级 */
    private Map<String, User> leaderIndex = Map.of();

    private Set<String> takenUsernames = Set.of();
    private Set<String> takenMobiles = Set.of();
    private Set<String> takenWorkNumbers = Set.of();
    private Set<String> takenIdCardHashes = Set.of();

    /** 每次导入开始时调用一次 */
    public void reload() {
        deptIndex = buildDeptIndex();
        roleIndex = buildRoleIndex();
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername, User::getMobile, User::getWorkNumber,
                        User::getRealName, User::getIdCardHash));

        Map<String, User> leaders = new HashMap<>();
        Set<String> usernames = new HashSet<>();
        Set<String> mobiles = new HashSet<>();
        Set<String> workNumbers = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (User user : users) {
            if (StringUtils.hasText(user.getWorkNumber())) {
                leaders.putIfAbsent(lower(user.getWorkNumber()), user);
                workNumbers.add(lower(user.getWorkNumber()));
            }
            if (StringUtils.hasText(user.getUsername())) {
                usernames.add(lower(user.getUsername()));
            }
            if (StringUtils.hasText(user.getMobile())) {
                mobiles.add(user.getMobile().trim());
            }
            if (StringUtils.hasText(user.getIdCardHash())) {
                hashes.add(user.getIdCardHash());
            }
        }
        // 工号占用含已逻辑删除（业务约定工号不回收），"存活行"那轮查不到它们，
        // 所以单独再取一次全量；放在这里而不是逐行查，是为了避免 5000 行 = 5000 次往返
        for (String workNumber : userMapper.selectAllWorkNumbersIncludingDeleted()) {
            if (StringUtils.hasText(workNumber)) {
                workNumbers.add(lower(workNumber));
            }
        }
        leaderIndex = leaders;
        takenUsernames = usernames;
        takenMobiles = mobiles;
        takenWorkNumbers = workNumbers;
        takenIdCardHashes = hashes;
    }

    public Department dept(String text) {
        return text == null ? null : deptIndex.get(lower(text));
    }

    public Role role(String text) {
        return text == null ? null : roleIndex.get(lower(text));
    }

    public User byWorkNumber(String workNumber) {
        return workNumber == null ? null : leaderIndex.get(lower(workNumber));
    }

    public boolean usernameTaken(String username) {
        return takenUsernames.contains(lower(username));
    }

    public boolean mobileTaken(String mobile) {
        return mobile != null && takenMobiles.contains(mobile.trim());
    }

    /**
     * 工号是否已被占用（含已逻辑删除）。
     *
     * <p>纯内存判断：{@link #reload()} 已把「存活 + 已删」的全部工号装进集合。</p>
     */
    public boolean workNumberTaken(String workNumber) {
        return workNumber != null && takenWorkNumbers.contains(lower(workNumber));
    }

    public boolean idCardHashTaken(String hash) {
        return hash != null && takenIdCardHashes.contains(hash);
    }

    /** 导入过程中新占用的值要立刻登记，否则同文件内第 3 行会和第 1 行撞车 */
    public void rememberUsername(String username) {
        if (StringUtils.hasText(username)) {
            takenUsernames.add(lower(username));
        }
    }

    public void rememberMobile(String mobile) {
        if (StringUtils.hasText(mobile)) {
            takenMobiles.add(mobile.trim());
        }
    }

    public void rememberWorkNumber(String workNumber) {
        if (StringUtils.hasText(workNumber)) {
            takenWorkNumbers.add(lower(workNumber));
        }
    }

    public void rememberIdCardHash(String hash) {
        if (StringUtils.hasText(hash)) {
            takenIdCardHashes.add(hash);
        }
    }

    private Map<String, Department> buildDeptIndex() {
        List<Department> depts = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().eq(Department::getIsDeleted, 0));
        Map<String, Department> index = new HashMap<>();
        for (Department dept : depts) {
            index.putIfAbsent(lower(dept.getDeptName()), dept);
            if (StringUtils.hasText(dept.getDeptCode())) {
                index.putIfAbsent(lower(dept.getDeptCode()), dept);
            }
        }
        return index;
    }

    private Map<String, Role> buildRoleIndex() {
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>().eq(Role::getIsDeleted, 0));
        Map<String, Role> index = new HashMap<>();
        for (Role role : roles) {
            index.putIfAbsent(lower(role.getRoleName()), role);
            if (StringUtils.hasText(role.getRoleCode())) {
                index.putIfAbsent(lower(role.getRoleCode()), role);
            }
        }
        return index;
    }

    private static String lower(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
