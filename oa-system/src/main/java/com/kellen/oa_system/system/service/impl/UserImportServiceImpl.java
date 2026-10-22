package com.kellen.oa_system.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.util.ExcelImportUtil;
import com.kellen.oa_system.system.dto.UserSaveRequest;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.Role;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.enums.GenderEnum;
import com.kellen.oa_system.system.mapper.DepartmentMapper;
import com.kellen.oa_system.system.mapper.RoleMapper;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.UserImportService;
import com.kellen.oa_system.system.service.UserService;
import com.kellen.oa_system.system.vo.UserImportErrorVO;
import com.kellen.oa_system.system.vo.UserImportResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 员工批量导入实现。
 *
 * <p>策略为<b>整批校验、全对才入库</b>：先把整个文件逐行校验完毕，
 * 任一行有问题就一行都不写，返回「第 N 行：原因」明细。这样既不会出现
 * 「导了一半」的中间态数据，管理员也能拿着行号直接改表重传。</p>
 *
 * <p>入库阶段复用 {@link UserService#saveUser}，与手工新增共用
 * 默认密码、工号生成、公司归属推导、角色绑定等全部业务规则，
 * 避免「导入进来的员工」和「手工新增的员工」规则不一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserImportServiceImpl implements UserImportService {

    /** 登录账号：3~30 位字母、数字、下划线 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,30}$");

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** 入职日期允许的写法：2026-09-29 / 2026/9/29 / 2026年9月29日 */
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"));

    /** 错误明细最多返回条数，防止一次几千条把响应撑爆 */
    private static final int MAX_ERROR_DETAIL = 200;

    private static final int COL_USERNAME = 0;
    private static final int COL_REAL_NAME = 1;
    private static final int COL_MOBILE = 2;
    private static final int COL_DEPT = 3;
    private static final int COL_POSITION = 4;
    private static final int COL_EMAIL = 5;
    private static final int COL_GENDER = 6;
    private static final int COL_ENTRY_DATE = 7;
    private static final int COL_WORK_NUMBER = 8;
    private static final int COL_ROLES = 9;

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final RoleMapper roleMapper;
    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserImportResultVO importUsers(MultipartFile file) {
        List<List<String>> sheet = ExcelImportUtil.readFirstSheet(file);
        assertHeaderMatched(sheet.get(0));

        // 一次性把部门 / 角色读进内存建索引，避免逐行查库
        Map<String, Department> deptIndex = buildDeptIndex();
        Map<String, Role> roleIndex = buildRoleIndex();

        List<UserSaveRequest> pending = new ArrayList<>();
        List<UserImportErrorVO> errors = new ArrayList<>();
        Set<String> seenUsername = new HashSet<>();
        Set<String> seenMobile = new HashSet<>();
        Set<String> seenWorkNumber = new HashSet<>();
        int totalRows = 0;

        for (int i = 1; i < sheet.size(); i++) {
            List<String> row = sheet.get(i);
            int rowNo = i + 1;
            if (ExcelImportUtil.isBlankRow(row)) {
                continue;
            }
            totalRows++;
            String error = parseRow(row, deptIndex, roleIndex,
                    seenUsername, seenMobile, seenWorkNumber, pending);
            if (error != null) {
                errors.add(new UserImportErrorVO(rowNo, error));
            }
        }

        if (totalRows == 0) {
            throw new BizException(ErrorCode.USER_IMPORT_EMPTY);
        }
        if (!errors.isEmpty()) {
            log.warn("批量导入校验未通过: 总行数={}, 失败={}", totalRows, errors.size());
            List<UserImportErrorVO> shown = errors.size() > MAX_ERROR_DETAIL
                    ? errors.subList(0, MAX_ERROR_DETAIL) : errors;
            return UserImportResultVO.builder()
                    .imported(false)
                    .totalRows(totalRows)
                    .successCount(0)
                    .failCount(errors.size())
                    .errors(shown)
                    .message("校验未通过，共 " + errors.size() + " 行存在问题，本次未导入任何数据，请修正后重新上传")
                    .build();
        }

        for (UserSaveRequest request : pending) {
            userService.saveUser(request);
        }
        log.info("批量导入员工成功: 共 {} 条", pending.size());
        return UserImportResultVO.builder()
                .imported(true)
                .totalRows(totalRows)
                .successCount(pending.size())
                .failCount(0)
                .errors(List.of())
                .message("导入成功，共新增 " + pending.size() + " 名员工，初始密码为系统默认初始密码")
                .build();
    }

    @Override
    public List<String> templateHeaders() {
        return List.of("登录账号", "姓名", "手机号", "部门", "岗位", "邮箱", "性别", "入职日期", "工号", "角色");
    }

    @Override
    public List<String> templateNotes() {
        return List.of(
                "填写说明：",
                "1. 请勿修改「员工数据」表的表头文字与列顺序，否则将按格式错误拒绝导入。",
                "2. 登录账号、姓名、手机号、部门为必填项；其余留空即可。",
                "3. 部门填部门名称（如「技术部」）或部门编码，需与系统内已有部门完全一致。",
                "4. 性别填「男」或「女」，留空默认男；入职日期留空默认取导入当天。",
                "5. 工号留空由系统按部门自动生成（部门拼音首字母 + 流水号），填写则以填写值为准。",
                "6. 角色可填多个，用英文逗号分隔（如「普通员工,人事专员」）；留空默认分配「普通员工」，更高角色由管理员后续在「编辑员工」里追加。",
                "7. 新增员工的初始密码统一为系统默认初始密码，导入后请提醒员工尽快修改。",
                "8. 任意一行校验失败则整批不导入，请按返回的行号修正后重新上传。",
                "9. 单次导入不超过 " + ExcelImportUtil.MAX_DATA_ROWS + " 行。");
    }

    // ------------------------------------------------------------------
    // 校验与解析
    // ------------------------------------------------------------------

    /** 表头必须与模板一致，否则后续按固定列序号取值会张冠李戴 */
    private void assertHeaderMatched(List<String> header) {
        List<String> expected = templateHeaders();
        for (int i = 0; i < expected.size(); i++) {
            String actual = ExcelImportUtil.cellAt(header, i);
            if (!expected.get(i).equals(actual)) {
                throw new BizException(ErrorCode.USER_IMPORT_FILE_INVALID,
                        "第 " + (i + 1) + " 列表头应为「" + expected.get(i) + "」，请下载系统模板后重新填写");
            }
        }
    }

    /**
     * 解析一行数据；成功时把请求对象加入 {@code pending}，失败时返回错误原因。
     *
     * @return null 表示该行通过校验
     */
    private String parseRow(List<String> row,
                            Map<String, Department> deptIndex,
                            Map<String, Role> roleIndex,
                            Set<String> seenUsername,
                            Set<String> seenMobile,
                            Set<String> seenWorkNumber,
                            List<UserSaveRequest> pending) {
        String username = ExcelImportUtil.cellAt(row, COL_USERNAME);
        String realName = ExcelImportUtil.cellAt(row, COL_REAL_NAME);
        String mobile = ExcelImportUtil.cellAt(row, COL_MOBILE);
        String deptText = ExcelImportUtil.cellAt(row, COL_DEPT);

        if (!StringUtils.hasText(username)) {
            return "登录账号不能为空";
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return "登录账号「" + username + "」格式不正确（3~30 位字母、数字或下划线）";
        }
        if (!StringUtils.hasText(realName)) {
            return "姓名不能为空";
        }
        if (realName.length() > 20) {
            return "姓名「" + realName + "」超过 20 个字符";
        }
        if (!StringUtils.hasText(mobile)) {
            return "手机号不能为空";
        }
        if (!StringUtils.hasText(deptText)) {
            return "部门不能为空";
        }

        // 文件内重复必须在所有「格式类」校验之前判完：否则第 2 行因手机号非法被拦下时，
        // 它的账号就不会登记进 seenUsername，第 3 行的同账号重复要等管理员改完重传才报出来。
        // 先登记再校验，保证一次上传就把所有重复行一次报全。
        if (!seenUsername.add(normalize(username))) {
            return "登录账号「" + username + "」在文件中重复";
        }
        if (!seenMobile.add(mobile)) {
            return "手机号「" + mobile + "」在文件中重复";
        }
        String rawWorkNumber = ExcelImportUtil.cellAt(row, COL_WORK_NUMBER);
        if (StringUtils.hasText(rawWorkNumber) && !seenWorkNumber.add(normalize(rawWorkNumber))) {
            return "工号「" + rawWorkNumber + "」在文件中重复";
        }

        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            return "手机号「" + mobile + "」格式不正确";
        }

        Department dept = deptIndex.get(normalize(deptText));
        if (dept == null) {
            return "部门「" + deptText + "」在系统中不存在";
        }

        String email = ExcelImportUtil.cellAt(row, COL_EMAIL);
        if (StringUtils.hasText(email) && !EMAIL_PATTERN.matcher(email).matches()) {
            return "邮箱「" + email + "」格式不正确";
        }

        Integer gender = GenderEnum.defaultCode();
        String genderText = ExcelImportUtil.cellAt(row, COL_GENDER);
        if (StringUtils.hasText(genderText)) {
            GenderEnum parsed = GenderEnum.parse(genderText);
            if (parsed == null) {
                return "性别「" + genderText + "」无法识别，请填写「男」或「女」";
            }
            gender = parsed.getCode();
        }

        LocalDate entryDate = null;
        String entryDateText = ExcelImportUtil.cellAt(row, COL_ENTRY_DATE);
        if (StringUtils.hasText(entryDateText)) {
            entryDate = parseDate(entryDateText);
            if (entryDate == null) {
                return "入职日期「" + entryDateText + "」格式不正确，请填写如 2026-09-29";
            }
        }

        String workNumber = rawWorkNumber;
        if (StringUtils.hasText(workNumber) && workNumber.length() > 30) {
            return "工号「" + workNumber + "」超过 30 个字符";
        }

        List<Long> roleIds = List.of();
        String roleText = ExcelImportUtil.cellAt(row, COL_ROLES);
        if (StringUtils.hasText(roleText)) {
            List<Long> resolved = new ArrayList<>();
            for (String token : roleText.split("[,，]")) {
                String key = normalize(token);
                if (key.isEmpty()) {
                    continue;
                }
                Role role = roleIndex.get(key);
                if (role == null) {
                    return "角色「" + token.trim() + "」在系统中不存在";
                }
                resolved.add(role.getId());
            }
            roleIds = resolved;
        }

        // 库内重复
        if (existsUsername(username)) {
            return "登录账号「" + username + "」在系统中已存在";
        }
        if (existsMobile(mobile)) {
            return "手机号「" + mobile + "」在系统中已存在";
        }
        if (StringUtils.hasText(workNumber) && existsWorkNumber(workNumber)) {
            return "工号「" + workNumber + "」在系统中已存在";
        }

        UserSaveRequest request = new UserSaveRequest();
        request.setUsername(username);
        request.setRealName(realName);
        request.setMobile(mobile);
        request.setDeptId(dept.getId());
        request.setPosition(ExcelImportUtil.cellAt(row, COL_POSITION));
        request.setEmail(StringUtils.hasText(email) ? email : null);
        request.setGender(gender);
        request.setEntryDate(entryDate);
        request.setWorkNumber(StringUtils.hasText(workNumber) ? workNumber : null);
        request.setRoleIds(roleIds);
        request.setStatus(1);
        pending.add(request);
        return null;
    }

    private LocalDate parseDate(String text) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 逐个尝试下一个格式
            }
        }
        return null;
    }

    /** 部门索引：名称与编码均可作为键，统一小写便于容错 */
    private Map<String, Department> buildDeptIndex() {
        List<Department> depts = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().eq(Department::getIsDeleted, 0));
        Map<String, Department> index = new HashMap<>();
        if (CollectionUtils.isEmpty(depts)) {
            return index;
        }
        for (Department dept : depts) {
            index.putIfAbsent(normalize(dept.getDeptName()), dept);
            if (StringUtils.hasText(dept.getDeptCode())) {
                index.putIfAbsent(normalize(dept.getDeptCode()), dept);
            }
        }
        return index;
    }

    private Map<String, Role> buildRoleIndex() {
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>().eq(Role::getIsDeleted, 0));
        Map<String, Role> index = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(roles)) {
            return index;
        }
        for (Role role : roles) {
            index.putIfAbsent(normalize(role.getRoleName()), role);
            index.putIfAbsent(normalize(role.getRoleCode()), role);
        }
        return index;
    }

    private boolean existsUsername(String username) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getIsDeleted, 0));
        return count != null && count > 0;
    }

    private boolean existsMobile(String mobile) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getMobile, mobile)
                .eq(User::getIsDeleted, 0));
        return count != null && count > 0;
    }

    /** 工号查重含已删除用户：业务约定工号不回收、不复用 */
    private boolean existsWorkNumber(String workNumber) {
        Long count = userMapper.countByWorkNumberIncludingDeleted(workNumber, null);
        return count != null && count > 0;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
