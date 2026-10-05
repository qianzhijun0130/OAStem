package com.kellen.oa_system.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门视图对象（树形）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptVO {

    private Long id;

    private Long parentId;

    private String deptName;

    private String deptCode;

    /**
     * 工号前缀。为空表示尚未分配（首次新增该部门员工时由系统按部门名称自动生成）。
     */
    private String workNoPrefix;

    private Long companyId;

    private Long leaderId;

    /** 部门负责人姓名，由用户表关联查询带出 */
    private String leaderName;

    private Integer deptType;

    private Integer sort;

    private Integer status;

    private LocalDateTime createTime;

    private List<DeptVO> children;

    /** 返回一棵可安全追加子节点的空容器，避免调用方判空 */
    public List<DeptVO> getChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }
        return children;
    }
}
