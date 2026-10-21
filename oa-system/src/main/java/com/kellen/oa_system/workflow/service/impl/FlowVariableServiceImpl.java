package com.kellen.oa_system.workflow.service.impl;

import com.kellen.oa_system.system.entity.Form;
import com.kellen.oa_system.system.mapper.FormMapper;
import com.kellen.oa_system.workflow.dto.FormFieldSchemaItem;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.service.FlowVariableService;
import com.kellen.oa_system.workflow.service.NodeFormService;
import com.kellen.oa_system.workflow.util.FlowVariables;
import com.kellen.oa_system.workflow.vo.FlowVariableVO;
import com.kellen.oa_system.workflow.vo.NodeFormVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link FlowVariableService} 实现。
 *
 * <p><b>数据来源</b>：表单字段取自「发起环节主表单」的 {@code oa_form.field_schema}
 * （那里存了控件类型，是判断数字 / 日期 / 布尔的唯一依据）；系统变量取自
 * {@link FlowVariables#SYSTEM_VARS} 常量目录。</p>
 *
 * <p><b>失败策略</b>：模板不存在、未绑定表单、field_schema 解析失败一律返回空表单字段列表，
 * 不让「取变量」这个辅助能力阻断设计器打开——用户此时仍可用系统变量配条件。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowVariableServiceImpl implements FlowVariableService {

    private final FlowDefinitionMapper flowDefinitionMapper;
    private final FormMapper formMapper;
    private final NodeFormService nodeFormService;
    private final ObjectMapper objectMapper;

    @Override
    public FlowVariableVO listVariables(Long flowDefId) {
        FlowVariableVO vo = new FlowVariableVO();
        vo.setFormFields(loadFormFields(flowDefId));
        List<FlowVariableVO.VarItem> systemVars = new ArrayList<>();
        for (FlowVariables.SystemVar var : FlowVariables.SYSTEM_VARS) {
            systemVars.add(new FlowVariableVO.VarItem(var.getKey(), var.getLabel(), var.getType().getCode()));
        }
        vo.setSystemVars(systemVars);
        return vo;
    }

    @Override
    public Set<String> listValidKeys(Long flowDefId) {
        Set<String> keys = new LinkedHashSet<>();
        for (FlowVariableVO.VarItem item : loadFormFields(flowDefId)) {
            keys.add(item.getKey());
        }
        for (FlowVariables.SystemVar var : FlowVariables.SYSTEM_VARS) {
            keys.add(var.getKey());
        }
        return keys;
    }

    /** 取发起环节主表单的字段清单；取不到时返回空列表 */
    private List<FlowVariableVO.VarItem> loadFormFields(Long flowDefId) {
        if (flowDefId == null) {
            return List.of();
        }
        FlowDefinition fd = flowDefinitionMapper.selectById(flowDefId);
        if (fd == null || fd.getFlowCode() == null) {
            return List.of();
        }
        try {
            NodeFormVO primary = nodeFormService.getPrimaryFormByFlowCode(fd.getFlowCode());
            if (primary == null || primary.getFormId() == null) {
                return List.of();
            }
            Form form = formMapper.selectById(primary.getFormId());
            if (form == null || form.getFieldSchema() == null || form.getFieldSchema().isBlank()) {
                return List.of();
            }
            return parseSchema(form.getFieldSchema());
        } catch (Exception e) {
            // 变量清单是配置辅助信息：解析失败只影响下拉内容，不应让设计器不可用
            log.warn("解析表单字段清单失败，条件变量下拉将只显示系统变量: flowDefId={}", flowDefId, e);
            return List.of();
        }
    }

    /** 解析 field_schema，并把 FormCreate 控件类型映射为条件取值类型 */
    private List<FlowVariableVO.VarItem> parseSchema(String fieldSchema) {
        List<FormFieldSchemaItem> items;
        try {
            items = objectMapper.readValue(fieldSchema, new TypeReference<List<FormFieldSchemaItem>>() {
            });
        } catch (Exception e) {
            log.warn("field_schema 反序列化失败：{}", e.getMessage());
            return List.of();
        }
        List<FlowVariableVO.VarItem> result = new ArrayList<>();
        for (FormFieldSchemaItem item : items) {
            if (item == null || item.getProp() == null || item.getProp().isBlank()) {
                continue;
            }
            String label = item.getLabel() == null || item.getLabel().isBlank()
                    ? item.getProp() : item.getLabel();
            String type = FlowVariables.typeOfFormControl(item.getType()).getCode();
            result.add(new FlowVariableVO.VarItem(item.getProp().trim(), label, type));
        }
        return result;
    }
}
