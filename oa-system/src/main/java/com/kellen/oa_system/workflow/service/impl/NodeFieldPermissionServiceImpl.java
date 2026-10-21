package com.kellen.oa_system.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.security.SecurityContextUtil;
import com.kellen.oa_system.system.entity.Form;
import com.kellen.oa_system.system.mapper.FormMapper;
import com.kellen.oa_system.workflow.dto.NodeFieldPermissionRequest;
import com.kellen.oa_system.workflow.entity.FlowNodeForm;
import com.kellen.oa_system.workflow.entity.FlowNodeFormField;
import com.kellen.oa_system.workflow.mapper.FlowNodeFormFieldMapper;
import com.kellen.oa_system.workflow.mapper.FlowNodeFormMapper;
import com.kellen.oa_system.workflow.service.NodeFieldPermissionService;
import com.kellen.oa_system.workflow.vo.NodeFieldPermissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 节点表单字段权限实现。
 *
 * <p>核心约定：<b>未配置即默认只读</b>。因此读取时会用表单的 field_schema 全量字段
 * 做骨架，再把已配置项覆盖上去 —— 返回给前端的是完整清单，
 * 而不是「仅有配置项」的稀疏列表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeFieldPermissionServiceImpl implements NodeFieldPermissionService {

    /** 权限：只读 */
    private static final int PERM_READONLY = 1;
    /** 权限：可编辑 */
    private static final int PERM_EDITABLE = 2;
    /** 权限：隐藏 */
    private static final int PERM_HIDDEN = 3;

    /** 单次保存的字段数上限，防御异常大包 */
    private static final int MAX_FIELDS = 200;

    private final FlowNodeFormFieldMapper fieldMapper;
    private final FlowNodeFormMapper nodeFormMapper;
    private final FormMapper formMapper;

    @Override
    public NodeFieldPermissionVO getPermissions(Long flowDefId, String nodeKey, Long formId) {
        if (flowDefId == null || nodeKey == null || nodeKey.isBlank() || formId == null) {
            return empty(flowDefId, nodeKey, formId);
        }
        Form form = formMapper.selectById(formId);
        if (form == null) {
            throw new BizException(ErrorCode.FORM_NOT_FOUND);
        }

        NodeFieldPermissionVO vo = new NodeFieldPermissionVO();
        vo.setFlowDefId(flowDefId);
        vo.setNodeKey(nodeKey);
        vo.setFormId(formId);
        vo.setFormCode(form.getFormCode());
        vo.setFormName(form.getFormName());

        FlowNodeForm bind = nodeFormMapper.selectOne(new LambdaQueryWrapper<FlowNodeForm>()
                .eq(FlowNodeForm::getFlowDefId, flowDefId)
                .eq(FlowNodeForm::getNodeKey, nodeKey)
                .last("LIMIT 1"));
        vo.setNodeName(bind == null ? nodeKey : bind.getNodeName());

        // 已配置项
        List<FlowNodeFormField> configured = fieldMapper.selectList(
                new LambdaQueryWrapper<FlowNodeFormField>()
                        .eq(FlowNodeFormField::getFlowDefId, flowDefId)
                        .eq(FlowNodeFormField::getNodeKey, nodeKey));
        Map<String, Integer> permMap = configured.stream()
                .collect(Collectors.toMap(FlowNodeFormField::getProp,
                        f -> f.getPermission() == null ? PERM_READONLY : f.getPermission(),
                        (a, b) -> a));

        // 以表单字段清单为骨架，逐个填权限；清单里没有的已配置项（表单改版删过字段）直接忽略
        List<NodeFieldPermissionVO.FieldItem> items = new ArrayList<>();
        for (SchemaField sf : parseSchema(form.getFieldSchema())) {
            NodeFieldPermissionVO.FieldItem item = new NodeFieldPermissionVO.FieldItem();
            item.setProp(sf.prop());
            item.setLabel(sf.label());
            Integer perm = permMap.get(sf.prop());
            item.setPermission(perm == null ? PERM_READONLY : perm);
            item.setConfigured(perm != null);
            items.add(item);
        }
        vo.setFields(items);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePermissions(NodeFieldPermissionRequest req) {
        if (req.getFields().size() > MAX_FIELDS) {
            throw new BizException(ErrorCode.FIELD_PERM_INVALID,
                    "单节点字段数超过 " + MAX_FIELDS + "，请确认提交的是字段权限而非整份表单");
        }
        // 必须已绑定表单：字段权限依附于「节点↔表单」关系，无绑定时配置无处生效
        FlowNodeForm bind = nodeFormMapper.selectOne(new LambdaQueryWrapper<FlowNodeForm>()
                .eq(FlowNodeForm::getFlowDefId, req.getFlowDefId())
                .eq(FlowNodeForm::getNodeKey, req.getNodeKey())
                .last("LIMIT 1"));
        if (bind == null) {
            throw new BizException(ErrorCode.NODE_FORM_NOT_BOUND);
        }
        Form form = formMapper.selectById(req.getFormId());
        if (form == null) {
            throw new BizException(ErrorCode.FORM_NOT_FOUND);
        }

        // 校验字段确实存在于该表单：防止前端传入脏数据让权限表与表单脱节
        Set<String> schemaProps = parseSchema(form.getFieldSchema()).stream()
                .map(SchemaField::prop).collect(Collectors.toCollection(HashSet::new));
        for (NodeFieldPermissionRequest.Item item : req.getFields()) {
            if (!schemaProps.contains(item.getProp())) {
                throw new BizException(ErrorCode.FIELD_PERM_INVALID,
                        "字段「" + item.getProp() + "」不属于表单 " + form.getFormCode());
            }
        }

        // 全量覆盖：先逻辑删除该节点旧配置，再插入新配置
        fieldMapper.delete(new LambdaQueryWrapper<FlowNodeFormField>()
                .eq(FlowNodeFormField::getFlowDefId, req.getFlowDefId())
                .eq(FlowNodeFormField::getNodeKey, req.getNodeKey()));

        Long userId = SecurityContextUtil.getCurrentUserId();
        for (NodeFieldPermissionRequest.Item item : req.getFields()) {
            FlowNodeFormField row = new FlowNodeFormField();
            row.setFlowDefId(req.getFlowDefId());
            row.setNodeKey(req.getNodeKey());
            row.setNodeName(req.getNodeName());
            row.setFormId(req.getFormId());
            row.setProp(item.getProp());
            row.setFieldLabel(item.getFieldLabel());
            row.setPermission(item.getPermission());
            row.setCreateBy(userId);
            row.setUpdateBy(userId);
            fieldMapper.insert(row);
        }
        log.info("已保存节点字段权限: flowDefId={}, nodeKey={}, 共 {} 项", req.getFlowDefId(), req.getNodeKey(), req.getFields().size());
    }

    @Override
    public List<String> listEditableProps(Long flowDefId, String nodeKey, Long formId) {
        if (flowDefId == null || nodeKey == null || nodeKey.isBlank() || formId == null) {
            return List.of();
        }
        return fieldMapper.selectList(new LambdaQueryWrapper<FlowNodeFormField>()
                        .eq(FlowNodeFormField::getFlowDefId, flowDefId)
                        .eq(FlowNodeFormField::getNodeKey, nodeKey)
                        .eq(FlowNodeFormField::getFormId, formId)
                        .eq(FlowNodeFormField::getPermission, PERM_EDITABLE))
                .stream().map(FlowNodeFormField::getProp).toList();
    }

    // ------------------------- 内部辅助 -------------------------

    private NodeFieldPermissionVO empty(Long flowDefId, String nodeKey, Long formId) {
        NodeFieldPermissionVO vo = new NodeFieldPermissionVO();
        vo.setFlowDefId(flowDefId);
        vo.setNodeKey(nodeKey);
        vo.setFormId(formId);
        vo.setFields(List.of());
        return vo;
    }

    /** 表单字段清单中的单个字段 */
    private record SchemaField(String prop, String label) {
    }

    /**
     * 解析 oa_form.field_schema。
     *
     * <p>用正则而非 JSON 解析：字段清单是本服务自己写出的固定结构
     * （{@code {"prop":...,"label":...}}），用轻量解析避免为此引入 JsonNode 遍历。
     * 解析失败返回空列表 —— 字段权限只是附加能力，不应让配置页打不开。</p>
     */
    private List<SchemaField> parseSchema(String fieldSchema) {
        List<SchemaField> list = new ArrayList<>();
        if (fieldSchema == null || fieldSchema.isBlank()) {
            return list;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\{[^{}]*\"prop\"\\s*:\\s*\"([^\"]+)\"[^{}]*}")
                .matcher(fieldSchema);
        while (m.find()) {
            String prop = m.group(1);
            String label = extractLabel(m.group());
            list.add(new SchemaField(prop, label));
        }
        return list;
    }

    /** 从单个字段对象片段里取 label，取不到则回退为 prop */
    private String extractLabel(String jsonFragment) {
        java.util.regex.Matcher lm = java.util.regex.Pattern
                .compile("\"label\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(jsonFragment);
        return lm.find() ? lm.group(1) : "";
    }
}
