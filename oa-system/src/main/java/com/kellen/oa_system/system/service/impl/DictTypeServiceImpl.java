package com.kellen.oa_system.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.redis.RedisGuard;
import com.kellen.oa_system.system.dto.DictTypePageQuery;
import com.kellen.oa_system.system.dto.DictTypeSaveRequest;
import com.kellen.oa_system.system.entity.DictData;
import com.kellen.oa_system.system.entity.DictType;
import com.kellen.oa_system.system.mapper.DictDataMapper;
import com.kellen.oa_system.system.mapper.DictTypeMapper;
import com.kellen.oa_system.system.service.DictTypeService;
import com.kellen.oa_system.system.vo.DictTypeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据字典类型服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictTypeServiceImpl implements DictTypeService {

    private static final int STATUS_ENABLED = 1;

    private final DictTypeMapper dictTypeMapper;
    private final DictDataMapper dictDataMapper;
    private final RedisGuard redisGuard;

    @Override
    public PageResult<DictTypeVO> pageTypes(DictTypePageQuery query) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getDictName())) {
            wrapper.like(DictType::getDictName, query.getDictName());
        }
        if (StringUtils.hasText(query.getDictType())) {
            wrapper.like(DictType::getDictType, query.getDictType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(DictType::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(DictType::getId);

        long total = dictTypeMapper.selectCount(wrapper);
        if (total == 0) {
            return PageResult.empty((long) query.getPageNo(), (long) query.getPageSize());
        }
        Page<DictType> page = new Page<>(query.getPageNo(), query.getPageSize());
        dictTypeMapper.selectPage(page, wrapper);
        List<DictTypeVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, total, (long) query.getPageNo(), (long) query.getPageSize());
    }

    @Override
    public List<DictTypeVO> listEnabledTypes() {
        List<DictType> types = dictTypeMapper.selectList(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getStatus, STATUS_ENABLED)
                .orderByAsc(DictType::getId));
        return types.stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveType(DictTypeSaveRequest request) {
        if (request.getId() == null) {
            assertDictTypeAvailable(request.getDictType());
            DictType entity = new DictType();
            applyRequest(entity, request);
            entity.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
            dictTypeMapper.insert(entity);
            // 类型不存在时 listByType 会把「空列表」也缓存，所以新建类型同样要清
            evictCache(entity.getDictType());
            return entity.getId();
        }

        DictType existing = requireType(request.getId());
        // 类型编码是业务取值依据，一旦被引用就不该再变——改码等于让所有读取方失效
        if (!existing.getDictType().equals(request.getDictType())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "字典类型编码创建后不可修改");
        }
        // 只提交「id + 变更字段」的部分实体：若回写整实体，实体会带着从库里读到的旧
        // updateTime，MyBatis-Plus 的自动填充不覆盖非空值，旧值便被显式写进 SET，
        // 导致 MySQL 的 ON UPDATE CURRENT_TIMESTAMP 因「列被显式赋值且值未变」而不触发。
        DictType update = new DictType();
        update.setId(request.getId());
        applyRequest(update, request);
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        dictTypeMapper.updateById(update);
        // 改名/改排序/改状态都会影响下拉数据
        evictCache(existing.getDictType());
        return request.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        DictType existing = requireType(id);
        Long itemCount = dictDataMapper.selectCount(new LambdaQueryWrapper<DictData>()
                .eq(DictData::getDictType, existing.getDictType()));
        if (itemCount != null && itemCount > 0) {
            throw new BizException(ErrorCode.DICT_TYPE_HAS_DATA);
        }
        // 唯一性已由数据库保证：uk_dict_type 改为 (dict_type, active_flag)，
        // 已删行 active_flag=NULL，原编码自动释放，应用层无需改写业务值
        dictTypeMapper.deleteById(id);
        evictCache(existing.getDictType());
        log.info("字典类型已删除 id={}", id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        DictType existing = requireType(id);
        // 同上：只提交 id + status，让 update_time 走自动填充（与 UserServiceImpl#updateUserStatus 同口径）
        DictType update = new DictType();
        update.setId(id);
        update.setStatus(status);
        dictTypeMapper.updateById(update);
        // 停用/启用类型会让整组字典项「可用 → 不可用」，必须立刻失效缓存
        evictCache(existing.getDictType());
    }

    /** 失效某个字典类型的缓存，与 DictDataServiceImpl 共用 key 前缀 */
    private void evictCache(String dictType) {
        if (StringUtils.hasText(dictType)) {
            redisGuard.remove(DictDataServiceImpl.CACHE_PREFIX + dictType);
        }
    }

    // ---------- 内部方法 ----------

    private DictType requireType(Long id) {
        DictType entity = dictTypeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DICT_TYPE_NOT_FOUND);
        }
        return entity;
    }

    /** 校验 dict_type 未被占用。类型编码创建后不可修改，因此只需在新增时校验 */
    private void assertDictTypeAvailable(String dictType) {
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<DictType>()
                .eq(DictType::getDictType, dictType));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.DICT_TYPE_DUPLICATE);
        }
    }

    private void applyRequest(DictType entity, DictTypeSaveRequest request) {
        entity.setDictName(request.getDictName());
        entity.setDictType(request.getDictType());
        entity.setRemark(request.getRemark());
    }

    private DictTypeVO toVO(DictType entity) {
        DictTypeVO vo = new DictTypeVO();
        vo.setId(entity.getId());
        vo.setDictName(entity.getDictName());
        vo.setDictType(entity.getDictType());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
