package com.kellen.oa_system.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.system.dto.DictDataPageQuery;
import com.kellen.oa_system.system.dto.DictDataSaveRequest;
import com.kellen.oa_system.system.entity.DictData;
import com.kellen.oa_system.system.entity.DictType;
import com.kellen.oa_system.system.mapper.DictDataMapper;
import com.kellen.oa_system.system.mapper.DictTypeMapper;
import com.kellen.oa_system.system.service.DictDataService;
import com.kellen.oa_system.system.vo.DictDataVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据字典项服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictDataServiceImpl implements DictDataService {

    private static final int STATUS_ENABLED = 1;
    private static final int DEFAULT_YES = 1;
    private static final int DEFAULT_NO = 0;

    private final DictDataMapper dictDataMapper;
    private final DictTypeMapper dictTypeMapper;

    @Override
    public PageResult<DictDataVO> pageData(DictDataPageQuery query) {
        LambdaQueryWrapper<DictData> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getDictType())) {
            wrapper.eq(DictData::getDictType, query.getDictType());
        }
        if (StringUtils.hasText(query.getItemLabel())) {
            wrapper.like(DictData::getItemLabel, query.getItemLabel());
        }
        if (query.getStatus() != null) {
            wrapper.eq(DictData::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(DictData::getSort).orderByAsc(DictData::getId);

        long total = dictDataMapper.selectCount(wrapper);
        if (total == 0) {
            return PageResult.empty((long) query.getPageNo(), (long) query.getPageSize());
        }
        Page<DictData> page = new Page<>(query.getPageNo(), query.getPageSize());
        dictDataMapper.selectPage(page, wrapper);
        List<DictDataVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, total, (long) query.getPageNo(), (long) query.getPageSize());
    }

    @Override
    public List<DictDataVO> listByType(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "字典类型编码不能为空");
        }
        // 类型被停用则整组不可用：消费方（公告类型/级别、流程分类）的下拉应取不到值，
        // 否则「停用类型」就是一个不影响业务的空动作。类型不存在时同样返回空数组。
        Long enabledType = dictTypeMapper.selectCount(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getDictType, dictType)
                .eq(DictType::getStatus, STATUS_ENABLED));
        if (enabledType == null || enabledType == 0) {
            return List.of();
        }
        List<DictData> items = dictDataMapper.selectList(new LambdaQueryWrapper<DictData>()
                .eq(DictData::getDictType, dictType)
                .eq(DictData::getStatus, STATUS_ENABLED)
                .orderByAsc(DictData::getSort)
                .orderByAsc(DictData::getId));
        return items.stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveData(DictDataSaveRequest request) {
        requireTypeExists(request.getDictType());
        assertItemValueAvailable(request.getDictType(), request.getItemValue(), request.getId());

        boolean asDefault = request.getIsDefault() != null && request.getIsDefault() == DEFAULT_YES;
        if (asDefault) {
            clearDefault(request.getDictType(), request.getId());
        }

        if (request.getId() == null) {
            DictData entity = new DictData();
            applyRequest(entity, request);
            entity.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
            entity.setIsDefault(asDefault ? DEFAULT_YES : DEFAULT_NO);
            dictDataMapper.insert(entity);
            return entity.getId();
        }

        DictData existing = requireData(request.getId());
        // 只提交「id + 变更字段」的部分实体，避免整实体回写把旧 update_time 显式塞进 SET
        // 而使 ON UPDATE CURRENT_TIMESTAMP 失效（细节见 DictTypeServiceImpl#saveType）
        DictData update = new DictData();
        update.setId(existing.getId());
        applyRequest(update, request);
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        update.setIsDefault(asDefault ? DEFAULT_YES : DEFAULT_NO);
        dictDataMapper.updateById(update);
        return existing.getId();
    }

    @Override
    public void deleteData(Long id) {
        requireData(id);
        dictDataMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        requireData(id);
        // 同上：只提交 id + status，让 update_time 走自动填充
        DictData update = new DictData();
        update.setId(id);
        update.setStatus(status);
        dictDataMapper.updateById(update);
    }

    // ---------- 内部方法 ----------

    private DictData requireData(Long id) {
        DictData entity = dictDataMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DICT_DATA_NOT_FOUND);
        }
        return entity;
    }

    /** 字典项必须挂在一个真实存在的类型下，否则前端按类型取数会永远为空 */
    private void requireTypeExists(String dictType) {
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getDictType, dictType));
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.DICT_TYPE_NOT_FOUND, "字典类型不存在：" + dictType);
        }
    }

    /**
     * 同一类型下数据值不允许重复。
     *
     * <p>没建数据库唯一索引是因为逻辑删除会阻挡「删掉某项后重新加回同值」；
     * 交给业务层校验既能拦住重复，又不牺牲可用性。</p>
     */
    private void assertItemValueAvailable(String dictType, String itemValue, Long excludeId) {
        LambdaQueryWrapper<DictData> wrapper = new LambdaQueryWrapper<DictData>()
                .eq(DictData::getDictType, dictType)
                .eq(DictData::getItemValue, itemValue)
                .ne(excludeId != null, DictData::getId, excludeId);
        Long count = dictDataMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.DICT_DATA_DUPLICATE);
        }
    }

    /** 一个类型同时只能有一个默认项，设置新默认时先清掉旧的 */
    private void clearDefault(String dictType, Long excludeId) {
        DictData reset = new DictData();
        reset.setIsDefault(DEFAULT_NO);
        dictDataMapper.update(reset, new LambdaQueryWrapper<DictData>()
                .eq(DictData::getDictType, dictType)
                .eq(DictData::getIsDefault, DEFAULT_YES)
                .ne(excludeId != null, DictData::getId, excludeId));
    }

    private void applyRequest(DictData entity, DictDataSaveRequest request) {
        entity.setDictType(request.getDictType());
        entity.setItemLabel(request.getItemLabel());
        entity.setItemValue(request.getItemValue());
        entity.setSort(request.getSort() == null ? 0 : request.getSort());
        entity.setCssClass(request.getCssClass());
        entity.setRemark(request.getRemark());
    }

    private DictDataVO toVO(DictData entity) {
        DictDataVO vo = new DictDataVO();
        vo.setId(entity.getId());
        vo.setDictType(entity.getDictType());
        vo.setItemLabel(entity.getItemLabel());
        vo.setItemValue(entity.getItemValue());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setCssClass(entity.getCssClass());
        vo.setIsDefault(entity.getIsDefault());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
