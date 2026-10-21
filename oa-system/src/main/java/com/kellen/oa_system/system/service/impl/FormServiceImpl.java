package com.kellen.oa_system.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.system.dto.FormPageQuery;
import com.kellen.oa_system.system.dto.FormSaveRequest;
import com.kellen.oa_system.system.entity.Form;
import com.kellen.oa_system.system.mapper.FormMapper;
import com.kellen.oa_system.system.service.FormService;
import com.kellen.oa_system.system.vo.FormVO;
import com.kellen.oa_system.workflow.entity.FlowNodeForm;
import com.kellen.oa_system.workflow.mapper.FlowNodeFormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 低代码表单管理实现。
 *
 * <p>关键约定：</p>
 * <ul>
 *   <li><b>更新必须刷新 update_time</b> —— 用「新建部分实体」只 set 变更字段，
 *       不回写 selectById 取回的整实体；否则旧 updateTime 会被显式写进 SET，
 *       MySQL 的 ON UPDATE CURRENT_TIMESTAMP 不触发（项目内已统一收口的坑）。</li>
 *   <li><b>表单编码查重要绕开 @TableLogic</b> —— 只查存活行（is_deleted=0），
 *       已删行靠 active_flag=NULL 让位，所以「已删除的编码」不算冲突。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormServiceImpl implements FormService {

    private final FormMapper formMapper;
    private final FlowNodeFormMapper flowNodeFormMapper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 单个表单规则的最大节点数，超出视为误传（如整份 Designer 页面结构），直接拒绝 */
    private static final int MAX_RULE_NODES = 200;

    @Override
    public PageResult<FormVO> pageForms(FormPageQuery query) {
        LambdaQueryWrapper<Form> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(Form::getFormCode, kw).or().like(Form::getFormName, kw));
        }
        if (query.getStatus() != null) {
            wrapper.eq(Form::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Form::getId);

        Page<Form> page = formMapper.selectPage(Page.of(query.getPageNo(), query.getPageSize()), wrapper);
        List<FormVO> records = new ArrayList<>(page.getRecords().size());
        for (Form form : page.getRecords()) {
            records.add(toVO(form, countBinds(form.getId())));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public FormVO getForm(Long id) {
        Form form = requireForm(id);
        return toVO(form, countBinds(form.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveForm(FormSaveRequest req) {
        String fieldSchema = deriveFieldSchema(req.getRuleJson());

        if (req.getId() == null) {
            assertCodeNotUsed(req.getFormCode(), null);
            Form form = new Form();
            form.setFormCode(req.getFormCode());
            form.setFormName(req.getFormName());
            form.setCategory(req.getCategory());
            form.setRuleJson(req.getRuleJson());
            form.setFieldSchema(fieldSchema);
            form.setStatus(req.statusOrDefault());
            form.setRemark(req.getRemark());
            formMapper.insert(form);
            return form.getId();
        }

        Form existing = requireForm(req.getId());
        assertCodeNotUsed(req.getFormCode(), existing.getId());

        // 部分实体：只 set 变更字段，保证 update_time 走 MySQL ON UPDATE
        Form patch = new Form();
        patch.setId(existing.getId());
        patch.setFormCode(req.getFormCode());
        patch.setFormName(req.getFormName());
        patch.setCategory(req.getCategory());
        patch.setRuleJson(req.getRuleJson());
        patch.setFieldSchema(fieldSchema);
        patch.setStatus(req.statusOrDefault());
        patch.setRemark(req.getRemark());
        formMapper.updateById(patch);
        return existing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForm(Long id) {
        requireForm(id);
        if (countBinds(id) > 0) {
            throw new BizException(ErrorCode.FORM_IN_USE);
        }
        formMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        requireForm(id);
        Form patch = new Form();
        patch.setId(id);
        patch.setStatus(status);
        formMapper.updateById(patch);
    }

    @Override
    public List<FormVO> listEnabledOptions() {
        List<Form> forms = formMapper.selectList(new LambdaQueryWrapper<Form>()
                .eq(Form::getStatus, 1)
                .orderByAsc(Form::getId));
        List<FormVO> list = new ArrayList<>(forms.size());
        for (Form f : forms) {
            // 下拉只需标识与名称，规则 JSON 体量大且此处用不上
            FormVO vo = new FormVO();
            vo.setId(f.getId());
            vo.setFormCode(f.getFormCode());
            vo.setFormName(f.getFormName());
            vo.setCategory(f.getCategory());
            vo.setStatus(f.getStatus());
            vo.setBindCount(countBinds(f.getId()));
            list.add(vo);
        }
        return list;
    }

    // ------------------------- 内部辅助 -------------------------

    private Form requireForm(Long id) {
        Form form = formMapper.selectById(id);
        if (form == null) {
            throw new BizException(ErrorCode.FORM_NOT_FOUND);
        }
        return form;
    }

    /**
     * 表单编码查重（只查存活行）。
     *
     * <p>已逻辑删除的行 is_deleted=1、被 @TableLogic 自动排除，故这里无需手动加条件。</p>
     */
    private void assertCodeNotUsed(String formCode, Long selfId) {
        LambdaQueryWrapper<Form> wrapper = new LambdaQueryWrapper<Form>().eq(Form::getFormCode, formCode);
        if (selfId != null) {
            wrapper.ne(Form::getId, selfId);
        }
        if (formMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.FORM_CODE_DUPLICATE);
        }
    }

    private int countBinds(Long formId) {
        return flowNodeFormMapper.selectCount(
                new LambdaQueryWrapper<FlowNodeForm>().eq(FlowNodeForm::getFormId, formId)).intValue();
    }

    /**
     * 校验 FormCreate 规则 JSON 并派生字段清单。
     *
     * <p>只做两件必要的事：① 必须是 JSON 数组（Designer 产出的顶层结构）；
     * ② 抽出每个控件的 prop/label/type/required 组成 fieldSchema。
     * 不做结构改写，保证「设计器所见 = 运行时所得」。</p>
     */
    private String deriveFieldSchema(String ruleJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(ruleJson);
        } catch (Exception e) {
            throw new BizException(ErrorCode.FORM_RULE_INVALID, "规则 JSON 解析失败：" + e.getMessage());
        }
        if (root == null || !root.isArray()) {
            throw new BizException(ErrorCode.FORM_RULE_INVALID, "规则必须是 JSON 数组");
        }
        if (root.size() > MAX_RULE_NODES) {
            throw new BizException(ErrorCode.FORM_RULE_INVALID,
                    "规则节点数超过 " + MAX_RULE_NODES + "，请确认提交的是表单规则而非整份页面结构");
        }

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (JsonNode node : root) {
            // 布局类控件（divider/row 等）没有 prop，不参与数据绑定，直接跳过
            JsonNode propNode = node.get("prop");
            if (propNode == null || !propNode.isTextual() || propNode.asText().isBlank()) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            String prop = propNode.asText();
            // 标签字段双兼容：FormCreate 3.x 的规则用 title，存量数据用 label。
            // 只读 label 会让新设计的表单在派生清单里退化成一串 prop，列表与字段权限页都无法展示中文名。
            String label = node.path("title").asText(node.path("label").asText(prop));
            String type = node.path("type").asText("input");
            boolean required = node.path("rule").path("required").asBoolean(false);
            sb.append("{\"prop\":").append(jsonStr(prop))
              .append(",\"label\":").append(jsonStr(label))
              .append(",\"type\":").append(jsonStr(type))
              .append(",\"required\":").append(required)
              .append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private String jsonStr(String s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (Exception e) {
            // 理论上不会发生（String 一定能序列化）；真发生则用最保守的手工转义
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

    private FormVO toVO(Form form, int bindCount) {
        FormVO vo = new FormVO();
        vo.setId(form.getId());
        vo.setFormCode(form.getFormCode());
        vo.setFormName(form.getFormName());
        vo.setCategory(form.getCategory());
        vo.setRuleJson(form.getRuleJson());
        vo.setFieldSchema(form.getFieldSchema());
        vo.setStatus(form.getStatus());
        vo.setRemark(form.getRemark());
        vo.setBindCount(bindCount);
        vo.setCreateTime(fmt(form.getCreateTime()));
        vo.setUpdateTime(fmt(form.getUpdateTime()));
        return vo;
    }

    private String fmt(LocalDateTime time) {
        return time == null ? null : TS.format(time);
    }
}
