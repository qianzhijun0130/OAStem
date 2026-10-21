package com.kellen.oa_system.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.system.entity.Company;

/**
 * 公司 Mapper。
 *
 * <p>当前只用于审批列表把 {@code oa_department.company_id} 批量翻译成公司名，
 * 无自定义 SQL。逻辑删除条件由 {@code AuditEntity} 上的 {@code @TableLogic} 自动追加。</p>
 */
public interface CompanyMapper extends BaseMapper<Company> {
}
