package com.kellen.oa_system.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kellen.oa_system.system.entity.LoginLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface LoginLogMapper extends BaseMapper<LoginLog> {

    /**
     * 把清理截止时间之前的登录日志搬入归档表（与 OperationLogMapper#archiveBefore 同一口径）。
     *
     * @param before 清理截止时间（不含）
     * @return 实际归档行数
     */
    @Insert("INSERT IGNORE INTO oa_login_log_archive "
            + "(id, user_id, username, login_type, ip_address, location, browser, os, "
            + " login_status, fail_reason, create_time) "
            + "SELECT id, user_id, username, login_type, ip_address, location, browser, os, "
            + " login_status, fail_reason, create_time "
            + "FROM oa_login_log WHERE create_time < #{before}")
    int archiveBefore(@Param("before") LocalDateTime before);
}
