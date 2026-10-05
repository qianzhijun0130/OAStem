package com.kellen.oa_system.system.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.system.dto.LoginLogPageQuery;
import com.kellen.oa_system.system.vo.LoginLogVO;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDateTime;

/**
 * 登录日志服务。
 *
 * <p>本服务只提供查询、导出与清理——写入由 {@code AuthServiceImpl} 在登录流程内完成，
 * 因为登录日志需要记录「认证失败」这一业务结果，这是切面拿不到的语义。</p>
 */
public interface LoginLogService {

    PageResult<LoginLogVO> page(LoginLogPageQuery query);

    /** 按查询条件导出 xlsx（受导出上限保护） */
    void export(HttpServletResponse response, LoginLogPageQuery query);

    /**
     * 清理指定时间之前的日志。
     *
     * @param before 保留该时间（含）之后的日志
     * @return 实际删除条数
     */
    /**
     * 清理日志：先归档再物理删除。
     *
     * @param before 清理截止时间（不含）；{@code all=true} 时忽略此参数
     * @param all    是否全量清理。全量时以「本次请求开始时刻」为分界，
     *                保证本次操作自身的审计日志不会被自己删掉
     * @return 实际删除行数
     */
    int clean(LocalDateTime before, boolean all);
}
