package com.kellen.oa_system.system.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.system.dto.DictTypePageQuery;
import com.kellen.oa_system.system.dto.DictTypeSaveRequest;
import com.kellen.oa_system.system.vo.DictTypeVO;

import java.util.List;

/**
 * 数据字典类型服务。
 */
public interface DictTypeService {

    PageResult<DictTypeVO> pageTypes(DictTypePageQuery query);

    /** 全部启用中的字典类型，供下拉选择使用 */
    List<DictTypeVO> listEnabledTypes();

    /** @return 新增或修改后的主键 */
    Long saveType(DictTypeSaveRequest request);

    /** 逻辑删除。类型下仍有数据项时拒绝，避免留下一批孤儿字典项 */
    void deleteType(Long id);

    void updateStatus(Long id, Integer status);
}
