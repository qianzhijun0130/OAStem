package com.kellen.oa_system.system.service;

import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.system.dto.OperationLogPageQuery;
import com.kellen.oa_system.system.dto.OperationLogRecord;
import com.kellen.oa_system.system.vo.OperationLogVO;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDateTime;

/**
 * 操作日志服务。
 */
public interface OperationLogService {

    /** 落库一条操作日志，由切面调用 */
    void save(OperationLogRecord record);

    PageResult<OperationLogVO> page(OperationLogPageQuery query);

    /** 按查询条件导出 xlsx（受导出上限保护） */
    void export(HttpServletResponse response, OperationLogPageQuery query);

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
