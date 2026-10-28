package com.kellen.oa_system.hr.salary.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.salary.entity.SalaryRecord;
import com.kellen.oa_system.hr.salary.entity.SalaryRecordItem;
import com.kellen.oa_system.hr.salary.mapper.SalaryRecordItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 工资条明细行的重建。
 *
 * <p>核算与手工调整走同一个方法：先把该记录旧明细全删、再按金额重写。
 * 之所以不做增量对比，是因为「明细必须与表头金额完全一致」比省几条 SQL 重要——
 * 增量对比一旦漏掉某一种情况，工资条就会出现表头 8000、明细合计 7500 的对不上。</p>
 *
 * <p>金额为 0 的项不落行，工资条上不出现一堆 0.00。</p>
 */
@Component
@RequiredArgsConstructor
public class SalaryItemWriter {

    private final SalaryRecordItemMapper recordItemMapper;

    /** 重建某条核算记录的明细行；调用方必须在记录已落库（有 id）之后调用 */
    public void rebuild(SalaryRecord record) {
        recordItemMapper.delete(new LambdaQueryWrapper<SalaryRecordItem>()
                .eq(SalaryRecordItem::getRecordId, record.getId()));
        List<SalaryRecordItem> items = new ArrayList<>();
        add(items, record.getId(), "BASE", "基本工资", 1, record.getBaseSalary(), 1);
        add(items, record.getId(), "POST", "岗位工资", 1, record.getPostSalary(), 2);
        add(items, record.getId(), "PERF", "绩效工资", 1, record.getPerfSalary(), 3);
        add(items, record.getId(), "ALLOW", "固定津贴", 1, record.getAllowance(), 4);
        add(items, record.getId(), "OT", "加班费", 1, record.getOvertimePay(), 5);
        add(items, record.getId(), "BONUS", "其他收入", 1, record.getOtherIncome(), 6);
        add(items, record.getId(), "ATT", "考勤扣款", 2, record.getAttendanceDeduct(), 7);
        add(items, record.getId(), "SOCIAL", "社保个人", 2, record.getSocialPersonal(), 8);
        add(items, record.getId(), "FUND", "公积金个人", 2, record.getFundPersonal(), 9);
        add(items, record.getId(), "TAX", "个人所得税", 2, record.getTax(), 10);
        add(items, record.getId(), "OTHER", "其他扣款", 2, record.getOtherDeduct(), 11);
        for (SalaryRecordItem item : items) {
            recordItemMapper.insert(item);
        }
    }

    private void add(List<SalaryRecordItem> target, Long recordId, String code, String name,
                     int type, BigDecimal amount, int sort) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        SalaryRecordItem item = new SalaryRecordItem();
        item.setRecordId(recordId);
        item.setItemCode(code);
        item.setItemName(name);
        item.setItemType(type);
        item.setAmount(SalaryCalculator.scale(amount));
        item.setSort(sort);
        target.add(item);
    }
}
