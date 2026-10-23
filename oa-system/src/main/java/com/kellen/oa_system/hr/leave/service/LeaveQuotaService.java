package com.kellen.oa_system.hr.leave.service;

import com.kellen.oa_system.hr.leave.dto.QuotaAdjustRequest;
import com.kellen.oa_system.hr.leave.vo.LeaveQuotaVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 假期额度业务接口。
 *
 * <p>额度是「台账」不是「余额」：total/carryOver/used/locked 四个数各自独立，
 * 可用额度 = total + carryOver − used − locked。任何变更都走条件 UPDATE（见 Mapper）。</p>
 */
public interface LeaveQuotaService {

    /** 年度额度基数（按累计工龄：1~10 年 5 天、10~20 年 10 天、20 年以上 15 天） */
    BigDecimal annualLeaveDays(Long userId);

    /** 确保该员工该年度的额度行存在（年假按工龄初始化，调休从 0 起） */
    void ensureQuota(Long userId, int year);

    /** 占用额度；返回 false 表示可用额度不足 */
    boolean lock(Long userId, Integer leaveType, int year, BigDecimal days);

    void release(Long userId, Integer leaveType, int year, BigDecimal days);

    void consume(Long userId, Integer leaveType, int year, BigDecimal days);

    /** 销假差额退回 */
    void refund(Long userId, Integer leaveType, int year, BigDecimal days);

    /** 加班转调休：直接加总额度 */
    void increaseTotal(Long userId, Integer leaveType, int year, BigDecimal days);

    /** HR 视角：某年度额度（部门含下级） */
    List<LeaveQuotaVO> listByYear(int year, Long deptId);

    /** 我的额度 */
    List<LeaveQuotaVO> my(int year);

    /** 手工调整（覆盖 totalDays，记录调整说明） */
    void adjust(QuotaAdjustRequest request);
}
