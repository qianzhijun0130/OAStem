package com.kellen.oa_system.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 表单新增 / 编辑入参。
 *
 * <p>编辑复用同一 DTO：带 id 即为更新，不带 id 即为新增（与项目内用户管理一致的写法）。</p>
 */
@Data
public class FormSaveRequest {

    /** 表单 id；为空表示新增 */
    private Long id;

    /** 表单编码：字母开头，可含字母数字下划线短横 */
    @NotBlank(message = "表单编码不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{1,63}$", message = "表单编码须以字母开头，只能含字母、数字、下划线、短横，长度 2-64")
    private String formCode;

    @NotBlank(message = "表单名称不能为空")
    @Size(max = 100, message = "表单名称长度不能超过 100")
    private String formName;

    @Size(max = 32, message = "分类长度不能超过 32")
    private String category;

    /**
     * FormCreate 规则 JSON（option 数组），由可视化设计器产出。
     *
     * <p>用 String 接收而非 JSON 节点：前端传什么就存什么，后端只做「能否解析成数组」的
     * 轻量校验，不做结构改写，避免设计与运行之间的「静默清洗」导致设计器所见非所得。</p>
     */
    @NotBlank(message = "表单规则不能为空，请通过设计器生成")
    private String ruleJson;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 1启用 0停用；为空默认启用 */
    private Integer status;

    public int statusOrDefault() {
        return status == null ? 1 : status;
    }
}
