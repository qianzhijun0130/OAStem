package com.kellen.oa_system.system.support;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.util.IdCardUtil;
import com.kellen.oa_system.system.dto.UserSaveRequest;
import com.kellen.oa_system.system.entity.Department;
import com.kellen.oa_system.system.entity.Role;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.enums.GenderEnum;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 单行解析与校验。
 *
 * <p><b>每次导入 new 一个</b>（不是 Spring 单例）：它持有「本文件内已出现过的
 * 账号/手机号/身份证」这几个可变集合。做成单例会在线程间共享，
 * 两个人同时导入时对方的账号会被判成「文件内重复」——这种错只在并发时出现，
 * 单测和日常使用都碰不到。</p>
 *
 * <p>校验顺序刻意是「必填 → 文件内重复 → 格式 → 引用存在性 → 库内重复」：
 * 先报最基础的错，HR 改完一轮再发现更深的问题，比一次抛五类错误更好处理。
 * 唯一例外是重复登记必须早于格式校验，否则某行因手机号非法被拦下时它的账号
 * 没登记进集合，同文件里的重复行要等下一轮才报出来。</p>
 */
public class UserImportRowParser {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,30}$");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserImportLookup lookup;
    private final IdCardGuard idCardGuard;

    /** 本文件内已出现过的值（与库内占用分开，便于给出「文件内重复」这种更准确的提示） */
    private final Set<String> seenUsernames = new HashSet<>();
    private final Set<String> seenMobiles = new HashSet<>();
    private final Set<String> seenIdCards = new HashSet<>();

    public UserImportRowParser(UserImportLookup lookup, IdCardGuard idCardGuard) {
        this.lookup = lookup;
        this.idCardGuard = idCardGuard;
    }

    /**
     * 解析一行。
     *
     * @return 解析结果
     * @throws BizException 校验不通过；message 即该行的失败原因
     */
    public UserImportRow parse(int rowNo, List<String> row) {
        String username = UserImportCells.text(row, UserImportTemplate.COL_USERNAME);
        String realName = UserImportCells.text(row, UserImportTemplate.COL_REAL_NAME);
        String mobile = UserImportCells.text(row, UserImportTemplate.COL_MOBILE);
        String deptText = UserImportCells.text(row, UserImportTemplate.COL_DEPT);

        if (username.isEmpty()) {
            throw fail("登录账号不能为空");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw fail("登录账号「" + username + "」格式不正确（3~30 位字母、数字或下划线）");
        }
        if (realName.isEmpty()) {
            throw fail("姓名不能为空");
        }
        if (realName.length() > 20) {
            throw fail("姓名「" + realName + "」超过 20 个字符");
        }
        if (mobile.isEmpty()) {
            throw fail("手机号不能为空");
        }
        if (deptText.isEmpty()) {
            throw fail("部门不能为空");
        }

        // 重复登记先于格式校验（见类注释）
        if (!seenUsernames.add(lower(username))) {
            throw fail("登录账号「" + username + "」在文件中重复");
        }
        if (!seenMobiles.add(mobile)) {
            throw fail("手机号「" + mobile + "」在文件中重复");
        }
        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            throw fail("手机号「" + mobile + "」格式不正确");
        }

        Department dept = lookup.dept(deptText);
        if (dept == null) {
            throw fail("部门「" + deptText + "」在系统中不存在");
        }

        String email = UserImportCells.text(row, UserImportTemplate.COL_EMAIL);
        if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
            throw fail("邮箱「" + email + "」格式不正确");
        }

        Integer gender = GenderEnum.defaultCode();
        String genderText = UserImportCells.text(row, UserImportTemplate.COL_GENDER);
        if (!genderText.isEmpty()) {
            GenderEnum parsed = GenderEnum.parse(genderText);
            if (parsed == null) {
                throw fail("性别「" + genderText + "」无法识别，请填写「男」或「女」");
            }
            gender = parsed.getCode();
        }

        LocalDate entryDate = UserImportCells.date(UserImportCells.text(row, UserImportTemplate.COL_ENTRY_DATE));
        if (entryDate == null && !UserImportCells.text(row, UserImportTemplate.COL_ENTRY_DATE).isEmpty()) {
            throw fail("入职日期「" + UserImportCells.text(row, UserImportTemplate.COL_ENTRY_DATE)
                    + "」格式不正确，请填写如 2026-09-29");
        }
        // 入职日期留空取导入当天，与 UserServiceImpl#createUser 的默认值保持一致；
        // 这里必须先算出来，因为「待入职 or 试用」与「试用期截止日」都依赖它
        LocalDate effectiveEntry = entryDate == null ? LocalDate.now() : entryDate;

        String workNumber = UserImportCells.text(row, UserImportTemplate.COL_WORK_NUMBER);
        if (workNumber.length() > 30) {
            throw fail("工号「" + workNumber + "」超过 30 个字符");
        }

        List<Long> roleIds = parseRoles(UserImportCells.text(row, UserImportTemplate.COL_ROLES));

        // ---------- 库内重复 ----------
        if (lookup.usernameTaken(username)) {
            throw fail("登录账号「" + username + "」在系统中已存在");
        }
        if (lookup.mobileTaken(mobile)) {
            throw fail("手机号「" + mobile + "」在系统中已存在");
        }
        if (!workNumber.isEmpty() && lookup.workNumberTaken(workNumber)) {
            throw fail("工号「" + workNumber + "」在系统中已存在");
        }

        // ---------- 身份证 ----------
        String idCardText = UserImportCells.text(row, UserImportTemplate.COL_ID_CARD);
        String idCardHash = null;
        if (!idCardText.isEmpty()) {
            try {
                idCardHash = idCardGuard.hashOfNullable(idCardText);
            } catch (BizException e) {
                throw fail(e.getMessage());
            }
            if (!seenIdCards.add(idCardHash)) {
                throw fail("身份证「" + IdCardUtil.mask(idCardText) + "」在文件中重复");
            }
            if (lookup.idCardHashTaken(idCardHash)) {
                User holder = idCardGuard.findHolder(idCardHash, null);
                throw fail("该身份证已存在档案（工号 "
                        + (holder == null ? "未知" : holder.getWorkNumber()) + "，姓名 "
                        + (holder == null ? "未知" : holder.getRealName()) + "），请确认是否为重复建档");
            }
        }

        // ---------- 直属上级 ----------
        Long leaderId = null;
        String leaderWorkNumber = UserImportCells.text(row, UserImportTemplate.COL_LEADER_WORK_NUMBER);
        if (!leaderWorkNumber.isEmpty()) {
            User leader = lookup.byWorkNumber(leaderWorkNumber);
            if (leader == null) {
                throw fail("直属上级工号「" + leaderWorkNumber + "」在系统中不存在");
            }
            leaderId = leader.getId();
        }

        // ---------- 参加工作日期 ----------
        String workStartText = UserImportCells.text(row, UserImportTemplate.COL_WORK_START_DATE);
        LocalDate workStartDate = UserImportCells.date(workStartText);
        if (workStartDate == null && !workStartText.isEmpty()) {
            throw fail("参加工作日期「" + workStartText + "」格式不正确，请填写如 2020-07-01");
        }
        if (workStartDate != null && workStartDate.isAfter(LocalDate.now())) {
            throw fail("参加工作日期不能晚于今天");
        }

        // ---------- 合同与试用期 ----------
        UserImportContractParser.ContractInfo contract =
                UserImportContractParser.parse(row, effectiveEntry);

        // ---------- 任职状态 ----------
        Integer employmentStatus = parseEmploymentStatus(
                UserImportCells.text(row, UserImportTemplate.COL_EMPLOYMENT_STATUS), effectiveEntry);

        UserSaveRequest request = new UserSaveRequest();
        request.setUsername(username);
        request.setRealName(realName);
        request.setMobile(mobile);
        request.setDeptId(dept.getId());
        request.setPosition(UserImportCells.text(row, UserImportTemplate.COL_POSITION));
        request.setEmail(email.isEmpty() ? null : email);
        request.setGender(gender);
        request.setEntryDate(entryDate);
        request.setWorkNumber(workNumber.isEmpty() ? null : workNumber);
        request.setRoleIds(roleIds);
        request.setStatus(1);
        request.setIdCard(idCardText.isEmpty() ? null : idCardText);
        request.setBirthDate(IdCardUtil.birthDateOf(idCardText));
        request.setDirectLeaderId(leaderId);
        request.setWorkStartDate(workStartDate);
        request.setProbationEndDate(contract.probationEnd());
        request.setEmploymentStatus(employmentStatus);

        // 登记新占用，供后续行判断（同文件内第 3 行不能与第 1 行撞车）
        lookup.rememberUsername(username);
        lookup.rememberMobile(mobile);
        if (request.getWorkNumber() != null) {
            lookup.rememberWorkNumber(request.getWorkNumber());
        }
        if (idCardHash != null) {
            lookup.rememberIdCardHash(idCardHash);
        }

        return new UserImportRow(rowNo, request, contract.type(), contract.start(),
                contract.end(), contract.probationMonths());
    }

    // ------------------------------------------------------------------
    // 合同与试用期
    // ------------------------------------------------------------------

    /**
     * 任职状态：留空按入职日期判定（未到入职日 → 待入职，否则 → 试用）。
     *
     * <p>默认落「试用」而不是「正式」是本次的核心修正之一：旧实现走数据库默认值 3（正式），
     * 批量入职的人集体跳过试用期，转正提醒与试用期考核都不会触发。</p>
     */
    private Integer parseEmploymentStatus(String text, LocalDate entryDate) {
        if (text.isEmpty()) {
            return entryDate.isAfter(LocalDate.now()) ? 1 : 2;
        }
        return switch (text) {
            case "待入职", "1" -> 1;
            case "试用", "试用期", "2" -> 2;
            case "正式", "3" -> 3;
            default -> throw fail("任职状态「" + text + "」无法识别，请填写「待入职/试用/正式」");
        };
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private List<Long> parseRoles(String roleText) {
        if (roleText.isEmpty()) {
            return List.of();
        }
        List<Long> resolved = new ArrayList<>();
        for (String token : roleText.split("[,，]")) {
            if (token.isBlank()) {
                continue;
            }
            Role role = lookup.role(token);
            if (role == null) {
                throw fail("角色「" + token.trim() + "」在系统中不存在");
            }
            resolved.add(role.getId());
        }
        return resolved;
    }

    private static String lower(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static BizException fail(String message) {
        return new BizException(com.kellen.oa_system.common.ErrorCode.USER_IMPORT_FILE_INVALID, message);
    }

}
