package com.kellen.oa_system.workflow.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.workflow.dto.FlowDefinitionImportRequest;
import com.kellen.oa_system.workflow.dto.FlowDefinitionPageQuery;
import com.kellen.oa_system.workflow.dto.FlowDefinitionSaveRequest;
import com.kellen.oa_system.workflow.vo.FlowDefinitionVO;
import com.kellen.oa_system.workflow.vo.FlowOptionVO;

import java.util.List;

/**
 * 流程模板管理：列表、详情、保存（部署 BPMN）、删除（卸载部署）、启停。
 */
public interface FlowDefinitionService {

    PageResult<FlowDefinitionVO> pageDefinitions(FlowDefinitionPageQuery query);

    FlowDefinitionVO getDetail(Long id);

    /**
     * 列出启用中的流程模板选项（仅 id / flowCode / flowName / category / formFields）。
     *
     * <p>供「发起申请」页使用。该动作面向全体员工，不能沿用管理接口
     * {@code pageDefinitions} 的 {@code system:flow:query} 鉴权，否则专员 / 员工角色
     * 一打开页面就 403、模板下拉为空、根本无法发起。</p>
     */
    List<FlowOptionVO> listEnabledOptions();

    /** 返回模板对应的 BPMN XML（供前端 modeler 回显） */
    String getBpmnXml(Long id);

    /** 新增 / 更新模板；bpmnXml 非空时同步部署到 Flowable */
    Long saveDefinition(FlowDefinitionSaveRequest req);

    /**
     * 导入 BPMN XML 生成流程模板：后端解析流程 key / 名称 / 审批节点，
     * 生成 node_config 后部署到 Flowable 并入库。
     */
    Long importDefinition(FlowDefinitionImportRequest req);

    void deleteDefinition(Long id);

    void updateStatus(Long id, Integer status);
}
