package com.kellen.oa_system.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工（用户）视图对象（列表行）。
 *
 * <p>对外只暴露必要字段，密码等敏感信息绝不出现在此对象中。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;

    private String username;

    private String realName;

    private String nickname;

    private Integer gender;

    private String mobile;

    private String email;

    private Long companyId;

    private Long deptId;

    private String deptName;

    private String position;

    private String workNumber;

    private LocalDate entryDate;

    /** 角色ID集合 */
    private List<Long> roleIds;

    /** 角色名称（多个用顿号连接），便于列表展示 */
    private String roleNames;

    /** 状态：0禁用 1启用 2锁定 */
    private Integer status;

    private LocalDateTime createTime;
}
