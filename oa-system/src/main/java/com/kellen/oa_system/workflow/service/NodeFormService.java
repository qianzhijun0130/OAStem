package com.kellen.oa_system.workflow.service;

import com.kellen.oa_system.workflow.dto.NodeFormBindRequest;
import com.kellen.oa_system.workflow.vo.NodeFormVO;

import java.util.List;

/**
 * 流程节点↔表单 绑定管理，以及发起页所需的节点表单查询。
 */
public interface NodeFormService {

    /** 绑定（或重绑）某流程节点的表单：同节点已有绑定则先逻辑删除再新增 */
    void bindForm(NodeFormBindRequest req);

    void unbindForm(Long id);

    /** 列出某流程模板的全部节点表单绑定 */
    List<NodeFormVO> listByFlowDef(Long flowDefId);

    /**
     * 取某流程「发起环节」的主表单（{@code isPrimary=1}）。
     *
     * <p>供「发起申请」页渲染动态表单；未绑定时返回 {@code null}，
     * 由前端降级为「不渲染表单区」而不是报错——模板未配表单是合法状态。</p>
     */
    NodeFormVO getPrimaryFormByFlowCode(String flowCode);
}
