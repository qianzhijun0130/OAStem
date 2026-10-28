package com.kellen.oa_system.hr.attendance.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.attendance.entity.Attendance;
import com.kellen.oa_system.hr.common.support.HrDataScopeSupport;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import com.kellen.oa_system.system.service.support.DataScopeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 考勤的数据范围解析。
 *
 * <p><b>为什么抽出来</b>：列表、月汇总、导出三处都要「先判定可见人，再拼到查询上」。
 * 三处各写一遍，迟早有一处忘了「快照为空就抛错」这条——而漏掉的后果是导出接口静默返回全公司数据（BE-OPT-02）。</p>
 *
 * <p>约定：返回 {@code null} 表示不受限（全部数据），返回空列表表示一条都看不到。
 * 调用方必须区分这两种语义，不能用 {@code isEmpty()} 一把梭。</p>
 */
@Component
@RequiredArgsConstructor
public class AttendanceScopeSupport {

    private final DataScopeSupport dataScopeSupport;
    private final HrDataScopeSupport hrDataScopeSupport;
    private final UserMapper userMapper;

    /**
     * 数据范围 → 用户 ID 列表；返回 null 表示不受限。
     *
     * @param caller 调用方标识，只用于异常信息（拆分后本类无法再靠 {@code getClass()} 指出是谁漏了注解）
     */
    public List<Long> visibleUserIds(String caller) {
        // 快照为空 = 调用方漏标 @DataScope。返回 null 会被上层当成"不受限"，
        // 造成导出类接口静默越权（BE-OPT-02），因此直接失败而不是放行。
        if (dataScopeSupport.currentSnapshot() == null) {
            throw new IllegalStateException(
                    "调用方未标 @DataScope，数据权限无法判定：" + caller + "#visibleUserIds");
        }
        if (hrDataScopeSupport.isUnrestricted()) {
            return null;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        hrDataScopeSupport.applyToUser(wrapper);
        return userMapper.selectList(wrapper.select(User::getId)).stream().map(User::getId).toList();
    }

    /** 把数据范围直接拼到考勤查询上（按 user_id 收敛） */
    public void applyUserScope(LambdaQueryWrapper<Attendance> wrapper, String caller) {
        List<Long> ids = visibleUserIds(caller);
        if (ids == null) {
            return;
        }
        if (ids.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(Attendance::getUserId, ids);
    }
}
