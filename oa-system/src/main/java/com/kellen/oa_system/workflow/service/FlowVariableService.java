package com.kellen.oa_system.workflow.service;

import com.kellen.oa_system.workflow.vo.FlowVariableVO;

import java.util.Set;

/**
 * 分支条件「可用变量」服务。
 *
 * <p>职责单一：按流程模板解析出「哪些变量可以被条件引用」。设计器用它渲染下拉，
 * 保存校验用它判断变量名是否合法，两处共用同一份结果，避免出现
 * 「前端能选到、后端却判定不合法」的割裂。</p>
 */
public interface FlowVariableService {

    /**
     * 列出某流程模板在分支条件下可用的全部变量。
     *
     * @param flowDefId 流程模板 id
     * @return 变量清单；模板不存在或未绑定表单时表单字段为空数组
     */
    FlowVariableVO listVariables(Long flowDefId);

    /**
     * 列出某流程模板全部合法变量名（表单字段 + 系统内置变量）。
     *
     * <p>供保存时做「变量合法性」校验：条件里引用的变量必须在此集合内，
     * 否则运行时该变量取不到值，网关会静默走默认分支——这类错误在保存阶段拦住，
     * 成本远低于上线后排查「为什么流程走错了」。</p>
     */
    Set<String> listValidKeys(Long flowDefId);
}
