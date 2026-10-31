package com.kellen.oa_system.hr.common;

import com.kellen.oa_system.hr.attendance.support.AttendanceConstants;
import com.kellen.oa_system.hr.common.enums.LeaveTypeEnum;
import com.kellen.oa_system.hr.salary.support.SalaryConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 假别口径一致性契约（Q19）。
 *
 * <p>这个测试挡的是一类「改一处、另一处静默失效」的改动：月汇总把假别按<b>中文名</b>
 * 写进 {@code leave_days_json}，薪资核算再按中文名把天数取出来算扣款。两边只要有一处
 * 自己维护字面量，改名就会让 {@code getOrDefault(..., 0)} 取到 0——
 * 月汇总里明明有 3 天事假，工资条上扣款是 0，而且<b>不报任何错</b>。</p>
 *
 * <p>所以这里断言的不是「值等于某个字符串」，而是「所有入口都指向同一个来源」：
 * 即便将来把「事假」改名成「私事假」，只要只改枚举，本测试仍然全绿，
 * 而三条链路依然一致。如果想验证这一点，把枚举里的名字改一下再跑本测试即可。</p>
 */
class LeaveTypeConvergenceTest {

    @Test
    @DisplayName("假别映射只有一处来源：考勤汇总 / 请假额度 / 薪资扣款都取自枚举")
    void allLabelMapsComeFromEnum() {
        Map<Integer, String> fromEnum = LeaveTypeEnum.labelMap();
        assertEquals(8, fromEnum.size(), "假别应为 8 种（1~8）");

        // 考勤：月汇总写 leave_days_json 的键
        assertEquals(fromEnum, AttendanceConstants.LEAVE_TYPE_LABEL,
                "AttendanceConstants.LEAVE_TYPE_LABEL 必须与枚举一致，否则月汇总的键会与薪资侧对不上");

        // 薪资：按名字从 leave_days_json 取天数
        assertEquals(LeaveTypeEnum.PERSONAL.getLabel(), SalaryConstants.LEAVE_PERSONAL,
                "薪资扣款用的事假名称必须取自枚举，不能另写字面量");
        assertEquals(LeaveTypeEnum.SICK.getLabel(), SalaryConstants.LEAVE_SICK,
                "薪资扣款用的病假名称必须取自枚举，不能另写字面量");
    }

    @Test
    @DisplayName("编码 → 名称 → 编码 往返一致")
    void roundTrip() {
        for (LeaveTypeEnum type : LeaveTypeEnum.values()) {
            String label = LeaveTypeEnum.labelOf(type.getCode());
            assertNotNull(label, "编码 " + type.getCode() + " 缺名称");
            assertEquals(type.getCode(), LeaveTypeEnum.codeOf(label),
                    "名称「" + label + "」反查编码不一致");
        }
    }

    @Test
    @DisplayName("受地方政策限制的假别 = 婚假/产假/陪产假/丧假")
    void policyControlledCodes() {
        // 只有这四类由地方规定天数；事假/病假/年假/调休走额度或按单据天数
        assertEquals(java.util.Set.of(4, 5, 6, 7), LeaveTypeEnum.policyControlledCodes());
        assertTrue(LeaveTypeEnum.policyControlled().stream()
                        .allMatch(LeaveTypeEnum::isPolicyControlled),
                "policyControlled() 与 policyControlledCodes() 口径必须一致");
    }
}
