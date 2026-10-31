package com.kellen.oa_system.hr.employee.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.system.entity.User;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * 建档完整度的六个维度（BIZ-OPT-09）。
 *
 * <p>为什么做成枚举而不是六个方法：看板、清单、导出三处都要「维度编码 → 中文名 → 查询条件」。
 * 分开写的话，某天新增一个维度只改了看板，清单接口收到新编码就查不出东西，
 * 而页面只会显示一张空表——看起来像「这批人都填全了」。</p>
 *
 * <p>「在职」口径统一 {@code employment_status IN (2,3,4)}（试用/正式/待离职），
 * 与花名册、编制统计一致。已离职的人缺身份证不影响任何业务，不该出现在待办清单里，
 * 否则清单永远清不完。</p>
 */
public enum EmployeeCompletenessDimension {

    /** 无身份证：影响社保报送、个税专项附加扣除核对 */
    NO_ID_CARD("noIdCard", "无身份证",
            w -> w.and(x -> x.isNull(User::getIdCard).or().eq(User::getIdCard, ""))),

    /** 无在职合同：影响合同到期提醒、劳动争议举证 */
    NO_CONTRACT("noContract", "无在职合同",
            w -> w.notExists("SELECT 1 FROM oa_employee_contract c "
                    + "WHERE c.user_id = oa_user.id AND c.status = 1 AND c.is_deleted = 0")),

    /** 无参加工作日期：年假累计工龄算不出来，老员工会「没有年假」 */
    NO_WORK_START_DATE("noWorkStartDate", "无参加工作日期",
            w -> w.isNull(User::getWorkStartDate)),

    /** 无直属上级：审批人策略「部门主管」取不到人时只能落到部门负责人兜底 */
    NO_LEADER("noLeader", "无直属上级",
            w -> w.isNull(User::getDirectLeaderId)),

    /** 无薪资档案：核算时直接 6401 阻断，这个人当月发不出工资 */
    NO_SALARY_PROFILE("noSalaryProfile", "无薪资档案",
            w -> w.notExists("SELECT 1 FROM oa_salary_profile p "
                    + "WHERE p.user_id = oa_user.id AND p.is_deleted = 0")),

    /** 无银行账户：发薪时无法代发，只能发现金 */
    NO_BANK_ACCOUNT("noBankAccount", "无银行账户",
            w -> w.notExists("SELECT 1 FROM oa_employee_bank b "
                    + "WHERE b.user_id = oa_user.id AND b.is_deleted = 0 "
                    + "AND b.bank_account IS NOT NULL AND b.bank_account <> ''"));

    private final String code;
    private final String label;
    /**
     * 把「该维度缺失」的条件追加到查询上。
     *
     * <p>用 {@code Consumer} 而不是返回 SQL 字符串：条件里既有 MP 的
     * {@code isNull/or}，也有子查询 {@code notExists}，返回字符串会让调用方
     * 拼装时把括号拼错，而括号错一个的结果是「数出来的和列出来的不是一批人」。</p>
     */
    private final Consumer<LambdaQueryWrapper<User>> condition;

    EmployeeCompletenessDimension(String code, String label,
                                  Consumer<LambdaQueryWrapper<User>> condition) {
        this.code = code;
        this.label = label;
        this.condition = condition;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public Consumer<LambdaQueryWrapper<User>> getCondition() {
        return condition;
    }

    /** 按编码查找；未知编码返回 null（调用方据此报 400，而不是静默返回空表） */
    public static EmployeeCompletenessDimension ofCode(String code) {
        return Arrays.stream(values())
                .filter(d -> d.code.equalsIgnoreCase(code == null ? "" : code.trim()))
                .findFirst().orElse(null);
    }
}
