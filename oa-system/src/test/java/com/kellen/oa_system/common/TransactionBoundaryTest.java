package com.kellen.oa_system.common;

import com.kellen.oa_system.hr.attendance.service.impl.AttendanceServiceImpl;
import com.kellen.oa_system.hr.employee.service.impl.EmployeeServiceImpl;
import com.kellen.oa_system.hr.leave.service.impl.LeaveServiceImpl;
import com.kellen.oa_system.hr.performance.service.impl.PerfScoreServiceImpl;
import com.kellen.oa_system.hr.salary.service.impl.SalaryServiceImpl;
import com.kellen.oa_system.workflow.service.impl.ApprovalServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事务边界契约测试（BE-OPT-10）。
 *
 * <p>为什么要有它：`@Transactional(readOnly = true)` 与 `rollbackFor` 都是**只在运行时生效**的
 * 声明式配置——写错了编译能过、接口能跑，出问题时才发现「这个只读方法其实在写」
 * 或「这个写方法遇异常不回滚」。这些都属于沉默错误，用反射把契约钉死最便宜。</p>
 *
 * <p>测的是<b>注解被 Spring 识别后的最终属性</b>（走 {@link AnnotationTransactionAttributeSource}，
 * 与运行期同一套解析逻辑），不是简单读注解值，因此能同时覆盖「注解写错 Meta 注解」的情况。</p>
 *
 * <p>不需要 Spring 上下文与数据库：纯反射，毫秒级，可进每次构建。</p>
 */
class TransactionBoundaryTest {

    private final AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();

    /** 按方法名 + 参数个数定位（不依赖具体查询 DTO 类型，方法签名变化时会明确失败） */
    private Method method(Class<?> type, String name, int paramCount) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        throw new AssertionError("找不到方法: " + type.getSimpleName() + "#" + name + "/" + paramCount);
    }

    private TransactionAttribute attribute(Class<?> type, String name, int paramCount) {
        TransactionAttribute attr = source.getTransactionAttribute(method(type, name, paramCount), type);
        assertNotNull(attr, type.getSimpleName() + "#" + name + " 未声明事务");
        return attr;
    }

    @Test
    @DisplayName("只读方法必须声明 readOnly=true（为读写分离铺路，且禁止在只读方法里写库）")
    void readMethodsAreReadOnly() {
        assertTrue(attribute(SalaryServiceImpl.class, "pageRecords", 1).isReadOnly(), "薪资列表");
        assertTrue(attribute(SalaryServiceImpl.class, "myPayslips", 1).isReadOnly(), "我的工资条");
        assertTrue(attribute(AttendanceServiceImpl.class, "today", 0).isReadOnly(), "今日考勤");
        assertTrue(attribute(AttendanceServiceImpl.class, "pageMonth", 4).isReadOnly(), "月度考勤");
        assertTrue(attribute(PerfScoreServiceImpl.class, "distribution", 1).isReadOnly(), "等级分布");
        assertTrue(attribute(LeaveServiceImpl.class, "page", 1).isReadOnly(), "请假列表");
        assertTrue(attribute(ApprovalServiceImpl.class, "todo", 2).isReadOnly(), "待办列表");
        assertTrue(attribute(EmployeeServiceImpl.class, "detail", 1).isReadOnly(), "员工详情");
    }

    @Test
    @DisplayName("写方法不得被标成只读（否则 MySQL 会直接报 Connection is read-only）")
    void writeMethodsAreNotReadOnly() {
        assertFalse(attribute(SalaryServiceImpl.class, "calculate", 2).isReadOnly(), "薪资核算");
        assertFalse(attribute(AttendanceServiceImpl.class, "clock", 1).isReadOnly(), "打卡");
        assertFalse(attribute(LeaveServiceImpl.class, "submit", 1).isReadOnly(), "发起请假");
        assertFalse(attribute(ApprovalServiceImpl.class, "start", 1).isReadOnly(), "发起审批");
        assertFalse(attribute(PerfScoreServiceImpl.class, "calibrate", 1).isReadOnly(), "HR 校准");
        // rebuild 逐日重结 + 月结，是写事务；它内部的 settleDate/settleMonth 属自调用，注解不生效
        assertFalse(attribute(AttendanceServiceImpl.class, "rebuild", 1).isReadOnly(), "考勤重算");
    }

    @Test
    @DisplayName("所有声明式事务方法都必须能回滚 Exception（漏写 rollbackFor 会静默不回滚）")
    void allTransactionalMethodsRollbackOnCheckedException() {
        Class<?>[] types = {
                SalaryServiceImpl.class,
                AttendanceServiceImpl.class,
                PerfScoreServiceImpl.class,
                LeaveServiceImpl.class,
                ApprovalServiceImpl.class,
                EmployeeServiceImpl.class,
        };
        for (Class<?> type : types) {
            for (Method m : type.getDeclaredMethods()) {
                Transactional txn = AnnotationUtils.findAnnotation(m, Transactional.class);
                if (txn == null) {
                    continue;
                }
                TransactionAttribute attr = source.getTransactionAttribute(m, type);
                assertNotNull(attr, type.getSimpleName() + "#" + m.getName() + " 注解未被解析");
                if (attr.isReadOnly()) {
                    // 只读事务不写库，回滚规则无意义；它们刻意不带 rollbackFor
                    continue;
                }
                assertTrue(attr.rollbackOn(new Exception("probe")),
                        type.getSimpleName() + "#" + m.getName() + " 缺 rollbackFor = Exception.class，检查异常不会回滚");
            }
        }
    }
}
