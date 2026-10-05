package com.kellen.oa_system.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 「克隆角色菜单」入参。
 *
 * <p>背景：系统没有「角色继承 / 权限叠加」机制 —— 每个角色的菜单都必须独立完整勾选。
 * 现实中给新角色授权时，往往只是想以某个成熟角色（如「人事专员 HR」）为基线再增删，
 * 逐项重勾既低效又容易漏掉基础菜单（工作台、我的公告等），进而表现为
 * 「权限给了，但侧边栏缺节点、点不进去」。</p>
 *
 * <p>本 DTO 指定「以哪个角色为基线」，由服务端复制其已授权菜单集合。</p>
 */
@Data
public class RoleMenuCloneRequest {

    /** 作为基线的源角色ID：把该角色当前已授权的菜单复制到目标角色 */
    @NotNull(message = "请选择作为基线的源角色")
    private Long sourceRoleId;
}
