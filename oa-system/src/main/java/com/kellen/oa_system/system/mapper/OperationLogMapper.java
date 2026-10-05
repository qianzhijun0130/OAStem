package com.kellen.oa_system.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.system.entity.OperationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface OperationLogMapper extends BaseMapper<OperationLog> {

    /**
     * 把清理截止时间之前的操作日志搬入归档表。
     *
     * <p>同库 {@code INSERT ... SELECT}，与随后的 {@code DELETE} 处于同一事务，
     * 保证「归档成功才会删除」。使用 {@code INSERT IGNORE} 使重复归档幂等
     * （归档表主键沿用原日志 id）。</p>
     *
     * @param before 清理截止时间（不含）
     * @return 实际归档行数
     */
    @Insert("INSERT IGNORE INTO oa_operation_log_archive "
            + "(id, user_id, username, module, operation, method, request_params, response_result, "
            + " ip_address, location, cost_time, operation_status, error_msg, create_time) "
            + "SELECT id, user_id, username, module, operation, method, request_params, response_result, "
            + " ip_address, location, cost_time, operation_status, error_msg, create_time "
            + "FROM oa_operation_log WHERE create_time < #{before}")
    int archiveBefore(@Param("before") LocalDateTime before);
}
