package com.kellen.oa_system.workflow.service;

import com.kellen.oa_system.workflow.dto.NodeFieldPermissionRequest;
import com.kellen.oa_system.workflow.vo.NodeFieldPermissionVO;

import java.util.List;

/**
 * 节点表单字段权限服务。
 *
 * <p>约定：<b>未配置即默认只读</b>。渲染时以「有配置用配置、无配置按只读」为准。</p>
 */
public interface NodeFieldPermissionService {

    /**
     * 读取某节点在某表单上的全部字段权限。
     *
     * <p>返回的是表单的<b>全部字段</b>，未配置的以只读（permission=1, configured=false）补齐。</p>
     */
    NodeFieldPermissionVO getPermissions(Long flowDefId, String nodeKey, Long formId);

    /**
     * 保存某节点的字段权限（整节点全量覆盖）。
     *
     * <p>与 {@code oa_flow_node_form} 的绑定语义一致：先逻辑删除该节点旧配置再插入新配置。
     * 这样处理是因为唯一索引含 {@code active_flag}，直接 insert 会与旧行冲突，
     * 直接 update 又会让「哪些字段是本次新增的」变得含糊。</p>
     */
    void savePermissions(NodeFieldPermissionRequest request);

    /**
     * 读取某节点某表单的可编辑字段标识集合。
     *
     * <p>供运行时渲染与提交校验使用；无配置时返回空集合（全部只读）。</p>
     */
    List<String> listEditableProps(Long flowDefId, String nodeKey, Long formId);
}
