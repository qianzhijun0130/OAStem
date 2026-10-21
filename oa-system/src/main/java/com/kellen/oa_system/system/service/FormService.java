package com.kellen.oa_system.system.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.system.dto.FormPageQuery;
import com.kellen.oa_system.system.dto.FormSaveRequest;
import com.kellen.oa_system.system.vo.FormVO;

import java.util.List;

/**
 * 低代码表单管理：列表、详情、保存（含规则解析与字段派生）、删除、启停。
 */
public interface FormService {

    PageResult<FormVO> pageForms(FormPageQuery query);

    FormVO getForm(Long id);

    /** 新增 / 更新表单；保存时校验规则 JSON 并派生 fieldSchema */
    Long saveForm(FormSaveRequest req);

    void deleteForm(Long id);

    void updateStatus(Long id, Integer status);

    /**
     * 启用中的表单选项（只读、只含 id / code / name），供流程模板「绑定表单」下拉使用。
     *
     * <p>独立于 {@link #pageForms}：绑定动作发生在流程模板页，鉴权是
     * {@code system:flow:form-bind}，不应要求调用方同时具备表单管理权限。</p>
     */
    List<FormVO> listEnabledOptions();
}
