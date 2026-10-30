package com.kellen.oa_system.hr.recruit.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.hr.position.entity.Position;
import com.kellen.oa_system.hr.position.mapper.PositionMapper;
import com.kellen.oa_system.hr.recruit.entity.RecruitDemand;
import com.kellen.oa_system.system.entity.User;
import com.kellen.oa_system.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 编制缺口计算：岗位编制数 − 该部门该岗位的在岗人数。
 *
 * <p>口径只有一份：列表展示（配额列）与保存时的超编制日志必须一致，
 * 否则会出现「列表说超编、保存日志说没超」的自相矛盾。</p>
 *
 * <p>⚠️ 已知性能问题（保持原样，未在本轮优化）：{@code toDemandVOs} 每行都会调用
 * {@link #quotaOf}，而 {@code isOverQuota} 内部又调一次，等于 10 行记录打 40 次查询。
 * 要优化需改成批量取「部门+岗位」维度的在岗人数，属于独立改动。</p>
 */
@Component
@RequiredArgsConstructor
public class RecruitQuotaSupport {

    private final PositionMapper positionMapper;
    private final UserMapper userMapper;

    /** @return {@code [编制总数, 已用, 缺口]}；缺口不为负 */
    public int[] quotaOf(RecruitDemand demand) {
        Position position = positionMapper.selectById(demand.getPositionId());
        int total = position == null || position.getHeadcount() == null ? 0 : position.getHeadcount();
        Long used = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getDeptId, demand.getDeptId())
                .eq(User::getPositionId, demand.getPositionId())
                .eq(User::getIsDeleted, 0)
                .in(User::getEmploymentStatus, List.of(2, 3, 4)));
        int usedInt = used == null ? 0 : used.intValue();
        return new int[]{total, usedInt, Math.max(total - usedInt, 0)};
    }

    /** 申请人数 > 编制缺口 → 超编制（只提示不拦截） */
    public boolean isOverQuota(RecruitDemand demand) {
        int[] quota = quotaOf(demand);
        return demand.getHeadCount() != null && demand.getHeadCount() > quota[2];
    }
}
