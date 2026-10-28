package com.kellen.oa_system.hr.salary.support;

import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.hr.salary.entity.SalaryProfile;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * 金额与月份的无状态小工具。
 *
 * <p>全是纯函数、不碰库，所以核算、调整、状态推进三处用的是同一套「空值当 0」「标准工资四项相加」
 * 口径——这两个口径最容易各写一遍然后慢慢分叉。</p>
 */
public final class SalaryAmounts {

    private SalaryAmounts() {
    }

    /** 空值归零：核算链路上不区分「没填」和「填了 0」 */
    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 档案的标准工资 = 基本 + 岗位 + 绩效(基数) + 津贴 */
    public static BigDecimal standardOf(SalaryProfile profile) {
        return nz(profile.getBaseSalary()).add(nz(profile.getPostSalary()))
                .add(nz(profile.getPerfSalary())).add(nz(profile.getAllowance()));
    }

    /** 累计数的初值：收入 / 专项扣除 / 专项附加 / 已预扣个税 */
    public static BigDecimal[] zeroCumulative() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    /** 解析 yyyy-MM；空值取当月（核算页默认核算上个月，由前端传入） */
    public static YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month, SalaryConstants.MONTH_FORMAT);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "月份格式应为 yyyy-MM");
        }
    }
}
