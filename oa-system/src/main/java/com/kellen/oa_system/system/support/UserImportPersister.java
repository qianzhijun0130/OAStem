package com.kellen.oa_system.system.support;

import com.kellen.oa_system.hr.employee.entity.EmployeeContract;
import com.kellen.oa_system.hr.employee.mapper.EmployeeContractMapper;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 把一条解析好的导入数据落库（用户 + 合同）。
 *
 * <p><b>为什么单独成一个 Bean</b>：批量导入要「坏行跳过、其余照常入库」，
 * 所以编排层不能开事务——一旦外层有事务，某行抛异常会让 Spring 把整个事务标记为
 * rollback-only，即使你 catch 住了，最后 commit 也会抛 {@code UnexpectedRollbackException}，
 * 结果是「明明提示成功了 N 条，实际一条都没有」。</p>
 *
 * <p>因此事务边界下沉到这里：<b>每条记录一个事务</b>。用户与合同在同一次提交里，
 * 不会出现「账号建好了但合同没写上」的半成品；单行失败也只回滚这一行。</p>
 *
 * <p>注意必须由外部调用（Controller → Service → 本类），Spring 代理才生效。
 * 若哪天有人把它内联进 Service 的私有方法，{@code @Transactional} 会因自调用失效——
 * 那时「用户建成功、合同写失败」会留下无合同档案，且没有任何回滚。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserImportPersister {

    private final UserService userService;
    private final EmployeeContractMapper contractMapper;
    private final UserMapper userMapper;

    /**
     * 落库一条记录。
     *
     * @return 新建用户的 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long persist(UserImportRow row) {
        Long userId = userService.saveUser(row.request());
        if (row.contractType() != null) {
            contractMapper.insert(buildContract(userId, row));
        }
        return userId;
    }

    /**
     * 组装合同记录。
     *
     * <p>合同编号由系统生成（{@code HT-工号-开始日期}），不占模板一列：
     * 导入时 HR 手里通常只有「谁、什么类型、什么时候到期」，编号往往还没编；
     * 强制填会让一批人卡在最后一步。编号本身也要能读——它出现在合同管理的列表、
     * 导出与打印里，用 {@code userId} 拼一串数字没法跟纸质合同对上。</p>
     *
     * <p>工号取的是「落库后的真实工号」：A 列可能留空由系统按部门生成，
     * 用请求里的值会拿到 null。</p>
     */
    private EmployeeContract buildContract(Long userId, UserImportRow row) {
        EmployeeContract contract = new EmployeeContract();
        contract.setUserId(userId);
        contract.setContractNo(resolveContractNo(userId, row.contractStart()));
        contract.setContractType(row.contractType());
        contract.setStartDate(row.contractStart());
        contract.setEndDate(row.contractEnd());
        // 签订次数 1：这是入职首签。后续续签由合同管理的「续签」入口递增，
        // 不走这里——续签要判断「第二次固定期限到期后须签无固定期限」，属于合同管理自己的规则
        contract.setSignCount(1);
        contract.setSignDate(row.contractStart());
        // 状态 1 生效：导入的是当前在履行的合同
        contract.setStatus(1);
        contract.setRemark("批量导入建档");
        return contract;
    }

    private String resolveContractNo(Long userId, LocalDate startDate) {
        User created = userMapper.selectById(userId);
        String workNumber = created == null || created.getWorkNumber() == null
                ? String.valueOf(userId) : created.getWorkNumber();
        String datePart = (startDate == null ? LocalDate.now() : startDate)
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        return "HT-" + workNumber + "-" + datePart;
    }
}
