package com.kellen.oa_system.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.system.entity.Form;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单定义 Mapper。
 *
 * <p>表内 SQL 均为单表操作，用 MyBatis-Plus 通用能力即可；
 * 按项目约定不在 Mapper 层写业务 SQL。</p>
 */
@Mapper
public interface FormMapper extends BaseMapper<Form> {
}
