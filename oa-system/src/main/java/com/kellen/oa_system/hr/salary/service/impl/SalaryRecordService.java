package com.kellen.oa_system.hr.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.util.LogMasker;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.hr.salary.dto.SalaryAdjustRequest;
import com.kellen.oa_system.hr.salary.dto.SalaryPageQuery;
import com.kellen.oa_system.hr.salary.entity.SalaryAdjustLog;
import com.kellen.oa_system.hr.salary.entity.SalaryProfile;
import com.kellen.oa_system.hr.salary.entity.SalaryRecord;
import com.kellen.oa_system.hr.salary.mapper.SalaryAdjustLogMapper;
import com.kellen.oa_system.hr.salary.mapper.SalaryRecordMapper;
import com.kellen.oa_system.hr.salary.support.SalaryAmounts;
import com.kellen.oa_system.hr.salary.support.SalaryCalculator;
import com.kellen.oa_system.hr.salary.support.SalaryConstants;
import com.kellen.oa_system.hr.salary.support.SalaryItemWriter;
import com.kellen.oa_system.hr.salary.support.SalaryQueryContext;
import com.kellen.oa_system.hr.salary.support.SalaryRecordAssembler;
import com.kellen.oa_system.hr.salary.vo.SalaryPayslipVO;
import com.kellen.oa_system.hr.salary.vo.SalaryRecordVO;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 核算结果的管理视角：列表 / 工资条详情 / 手工调整 / 状态推进。
 *
 * <p>状态推进是一条单向链：草稿 → 已复核 → 已封账 → 已发放，每步只处理「当前状态 = 起点」的记录，
 * 条数为 0 就报错而不是静默返回——否则点了两次「复核」，第二次会让人误以为成功了。</p>
 *
 * <p>⚠️ 数据范围依赖门面上的 {@code @DataScope}（切面把快照写进 ThreadLocal）。
 * 门面方法上的注解一旦被拿掉，这里 {@code isUnrestricted()} 恒为 true，
 * 列表就会把全公司工资条放出去——这正是 BE-OPT-02 修掉的越权缺口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryRecordService {

    private final SalaryRecordMapper recordMapper;
    private final SalaryAdjustLogMapper adjustLogMapper;
    private final UserMapper userMapper;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final SalaryQueryContext queryContext;
    private final SalaryItemWriter itemWriter;
    private final SalaryRecordAssembler assembler;

    public PageResult<SalaryRecordVO> pageRecords(SalaryPageQuery query) {
        int no = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int size = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        LambdaQueryWrapper<SalaryRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getMonth())) {
            wrapper.eq(SalaryRecord::getMonth, query.getMonth());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SalaryRecord::getStatus, query.getStatus());
        }
        if (query.getDeptId() != null) {
            wrapper.eq(SalaryRecord::getDeptId, query.getDeptId());
        }
        Long viewer = SecurityContextUtil.getCurrentUserId();
        if (!hrDataScopeSupport.isUnrestricted()) {
            // 数据范围在用户层判定：薪资表只存了核算时的部门快照，直接按快照过滤会漏掉后来调部门的。
            // 注意：本方法必须带 @DataScope，否则 isUnrestricted() 恒为 true、整块被跳过 —— 这正是
            // BE-OPT-02 修复的越权缺口（任何持 hr:salary:calc 的账号都能看到全公司工资条）。
            LambdaQueryWrapper<User> scoped = new LambdaQueryWrapper<User>().select(User::getId);
            hrDataScopeSupport.applyToUser(scoped);
            List<Long> allowed = userMapper.selectList(scoped).stream().map(User::getId).toList();
            if (allowed.isEmpty()) {
                return PageResult.of(List.of(), 0L, (long) no, (long) size);
            }
            wrapper.in(SalaryRecord::getUserId, allowed);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            List<Long> matched = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .and(w -> w.like(User::getRealName, query.getKeyword())
                                    .or().like(User::getWorkNumber, query.getKeyword()))
                            .select(User::getId))
                    .stream().map(User::getId).toList();
            if (matched.isEmpty()) {
                return PageResult.of(List.of(), 0L, (long) no, (long) size);
            }
            wrapper.in(SalaryRecord::getUserId, matched);
        }
        wrapper.orderByDesc(SalaryRecord::getMonth).orderByAsc(SalaryRecord::getUserId);

        Page<SalaryRecord> page = recordMapper.selectPage(new Page<>(no, size), wrapper);
        List<SalaryRecordVO> vos = assembler.toRecordVOs(page.getRecords());
        log.debug("薪资列表查询: viewer={} total={}", viewer, page.getTotal());
        return PageResult.of(vos, page.getTotal(), (long) no, (long) size);
    }

    public SalaryPayslipVO detail(Long recordId) {
        SalaryRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.HR_SALARY_RECORD_NOT_FOUND);
        }
        return assembler.toPayslip(record, true);
    }

    public void adjust(Long recordId, SalaryAdjustRequest request) {
        SalaryRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.HR_SALARY_RECORD_NOT_FOUND);
        }
        if (record.getStatus() != null && record.getStatus() >= SalaryConstants.STATUS_LOCKED) {
            throw new BizException(ErrorCode.HR_SALARY_MONTH_LOCKED);
        }
        if (!StringUtils.hasText(request.getRemark())) {
            throw new BizException(ErrorCode.HR_SALARY_ADJUST_REASON_REQUIRED);
        }
        BigDecimal beforeIncome = SalaryAmounts.nz(record.getOtherIncome());
        BigDecimal beforeDeduct = SalaryAmounts.nz(record.getOtherDeduct());
        BigDecimal afterIncome = request.getOtherIncome() == null ? beforeIncome
                : SalaryAmounts.nz(request.getOtherIncome());
        BigDecimal afterDeduct = request.getOtherDeduct() == null ? beforeDeduct
                : SalaryAmounts.nz(request.getOtherDeduct());

        // 调整后必须重算应发/个税/实发，否则「改了收入但实发没变」
        SalaryProfile profile = queryContext.latestProfileAt(record.getUserId(),
                SalaryAmounts.parseMonth(record.getMonth()).atEndOfMonth());
        if (profile == null) {
            throw new BizException(ErrorCode.HR_SALARY_PROFILE_MISSING);
        }
        record.setOtherIncome(afterIncome);
        record.setOtherDeduct(afterDeduct);
        BigDecimal deltaIncome = afterIncome.subtract(beforeIncome);
        BigDecimal deltaDeduct = afterDeduct.subtract(beforeDeduct);
        record.setGrossPay(SalaryCalculator.scale(SalaryAmounts.nz(record.getGrossPay()).add(deltaIncome)));
        BigDecimal taxDelta = adjustTax(record, deltaIncome);
        record.setNetPay(SalaryCalculator.scale(SalaryAmounts.nz(record.getNetPay())
                .add(deltaIncome).subtract(deltaDeduct).subtract(taxDelta)));
        recordMapper.updateById(record);

        writeAdjustLog(recordId, "otherIncome", "其他收入", beforeIncome, afterIncome, request.getRemark());
        writeAdjustLog(recordId, "otherDeduct", "其他扣款", beforeDeduct, afterDeduct, request.getRemark());
        itemWriter.rebuild(record);
        log.info("薪资手工调整: recordId={} income {} deduct {} 原因={}", recordId,
                LogMasker.amountChange(beforeIncome, afterIncome),
                LogMasker.amountChange(beforeDeduct, afterDeduct), request.getRemark());
    }

    public int review(String month, Long deptId) {
        return transit(month, deptId, SalaryConstants.STATUS_DRAFT, SalaryConstants.STATUS_REVIEWED);
    }

    public int lock(String month, Long deptId) {
        return transit(month, deptId, SalaryConstants.STATUS_REVIEWED, SalaryConstants.STATUS_LOCKED);
    }

    public int pay(String month, Long deptId) {
        return transit(month, deptId, SalaryConstants.STATUS_LOCKED, SalaryConstants.STATUS_PAID);
    }

    /**
     * 手工调整只影响「本月」的个税差额。
     *
     * <p>严格做法要重算后续所有月份的累计，但那会让已发工资的历史记录被动改动。
     * 这里是「本月差额按边际税率补扣」——误差留到年度汇算，符合多数企业的操作习惯。</p>
     */
    private BigDecimal adjustTax(SalaryRecord record, BigDecimal deltaIncome) {
        if (deltaIncome.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal taxable = SalaryAmounts.nz(record.getCumulativeTaxable()).add(deltaIncome);
        BigDecimal recalculated = SalaryCalculator.cumulativeTax(taxable);
        BigDecimal before = SalaryCalculator.cumulativeTax(SalaryAmounts.nz(record.getCumulativeTaxable()));
        BigDecimal delta = SalaryCalculator.scale(recalculated.subtract(before));
        if (delta.compareTo(BigDecimal.ZERO) < 0) {
            delta = BigDecimal.ZERO;
        }
        record.setTax(SalaryCalculator.scale(SalaryAmounts.nz(record.getTax()).add(delta)));
        record.setCumulativeTaxable(SalaryCalculator.scale(taxable));
        return delta;
    }

    private void writeAdjustLog(Long recordId, String field, String label,
                                BigDecimal before, BigDecimal after, String reason) {
        if (before.compareTo(after) == 0) {
            return;
        }
        SalaryAdjustLog entry = new SalaryAdjustLog();
        entry.setRecordId(recordId);
        entry.setFieldName(field);
        entry.setFieldLabel(label);
        entry.setBeforeValue(before);
        entry.setAfterValue(after);
        entry.setReason(reason);
        entry.setOperatorId(SecurityContextUtil.getCurrentUserId());
        entry.setCreateTime(LocalDateTime.now());
        adjustLogMapper.insert(entry);
    }

    /** 状态推进：只处理「当前状态 = from」的记录，返回推进条数 */
    private int transit(String month, Long deptId, int from, int to) {
        if (!StringUtils.hasText(month)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "月份不能为空");
        }
        LambdaUpdateWrapper<SalaryRecord> wrapper = new LambdaUpdateWrapper<SalaryRecord>()
                .eq(SalaryRecord::getMonth, month)
                .eq(SalaryRecord::getStatus, from)
                .set(SalaryRecord::getStatus, to)
                .set(SalaryRecord::getUpdateTime, LocalDateTime.now());
        if (deptId != null) {
            wrapper.eq(SalaryRecord::getDeptId, deptId);
        }
        if (to == SalaryConstants.STATUS_REVIEWED) {
            wrapper.set(SalaryRecord::getReviewTime, LocalDateTime.now())
                    .set(SalaryRecord::getReviewBy, SecurityContextUtil.getCurrentUserId());
        }
        if (to == SalaryConstants.STATUS_PAID) {
            wrapper.set(SalaryRecord::getPayTime, LocalDateTime.now());
        }
        int count = recordMapper.update(null, wrapper);
        if (count == 0) {
            throw new BizException(ErrorCode.HR_SALARY_STATUS_INVALID,
                    "没有状态为「" + SalaryConstants.STATUS_LABEL.get(from) + "」的记录可推进，请先执行上一步");
        }
        log.info("薪资状态推进: month={} {}→{} 条数={}", month,
                SalaryConstants.STATUS_LABEL.get(from), SalaryConstants.STATUS_LABEL.get(to), count);
        return count;
    }
}
