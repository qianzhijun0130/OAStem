package com.kellen.oa_system.system.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.system.dto.DictDataPageQuery;
import com.kellen.oa_system.system.dto.DictDataSaveRequest;
import com.kellen.oa_system.system.vo.DictDataVO;

import java.util.List;

/**
 * 数据字典项服务。
 */
public interface DictDataService {

    PageResult<DictDataVO> pageData(DictDataPageQuery query);

    /**
     * 按类型编码取「启用中」的字典项，供业务下拉直接使用。
     *
     * <p>已按 sort 升序返回；停用的项不会出现，因此业务侧停用某个选项后
     * 前端下拉自动消失，无需改代码。</p>
     */
    List<DictDataVO> listByType(String dictType);

    /** @return 新增或修改后的主键 */
    Long saveData(DictDataSaveRequest request);

    void deleteData(Long id);

    void updateStatus(Long id, Integer status);
}
