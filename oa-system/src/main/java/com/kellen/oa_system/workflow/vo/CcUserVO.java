package com.kellen.oa_system.workflow.vo;

import lombok.Data;

/**
 * 抄送人候选：只暴露选择器需要的最小字段。
 *
 * <p>不复用用户管理 VO，是为了让没有「用户查询」权限的普通员工也能打开抄送弹窗。</p>
 */
@Data
public class CcUserVO {

    private Long id;

    private String realName;

    private String username;

    private String deptName;
}
