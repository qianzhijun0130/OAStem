package com.kellen.oa_system.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.workflow.entity.FlowNodeForm;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程节点表单绑定 Mapper。
 */
@Mapper
public interface FlowNodeFormMapper extends BaseMapper<FlowNodeForm> {
}
