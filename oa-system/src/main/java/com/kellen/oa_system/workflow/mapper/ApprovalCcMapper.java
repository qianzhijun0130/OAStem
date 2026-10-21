package com.kellen.oa_system.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.workflow.entity.ApprovalCc;

/**
 * 审批抄送 Mapper。
 *
 * <p>查询条件全部由 Service 用 Wrapper 组装（按 user_id / read_flag 过滤），
 * 无自定义 SQL。</p>
 */
public interface ApprovalCcMapper extends BaseMapper<ApprovalCc> {
}
