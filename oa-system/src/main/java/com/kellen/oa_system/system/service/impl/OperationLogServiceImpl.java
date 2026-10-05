package com.kellen.oa_system.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.system.dto.OperationLogPageQuery;
import com.kellen.oa_system.system.dto.OperationLogRecord;
import com.kellen.oa_system.system.entity.OperationLog;
import com.kellen.oa_system.system.mapper.OperationLogMapper;
import com.kellen.oa_system.system.service.OperationLogService;
import com.kellen.oa_system.system.service.support.LogScopeResolver;
import com.kellen.oa_system.system.vo.OperationLogVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    /** 导出上限：再多就该走离线导出，否则请求会长时间挂起 */
    private static final int EXPORT_LIMIT = 10000;

    /** 列表里 requestParams / errorMsg 的展示截断长度，完整内容走详情接口 */
    private static final int LIST_TEXT_LIMIT = 500;

    private static final int STATUS_SUCCESS = 1;

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final List<String> EXPORT_HEADERS = List.of(
            "日志ID", "操作账号", "模块", "操作描述", "请求方法",
            "请求参数", "操作IP", "耗时(ms)", "状态", "错误信息", "操作时间");

    /** 访问本类日志所需的功能权限码：无此权限者只能看自己的日志 */
    private static final String QUERY_AUTHORITY = "system:operation-log:query";

    /** 按时间清理时必须保留的最少天数：日志是事后追责的唯一凭据 */
    private static final int MIN_KEEP_DAYS = 1;

    private final OperationLogMapper operationLogMapper;
    private final LogScopeResolver logScopeResolver;

    /**
     * {@inheritDoc}
     *
     * <p>用 {@code REQUIRES_NEW} 独立事务：业务方法失败回滚时，这条「失败日志」必须留下来，
     * 否则最该被审计的异常操作反而查不到。日志自身失败绝不能影响业务，由切面兜底捕获。</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void save(OperationLogRecord record) {
        if (record == null) {
            return;
        }
        OperationLog entity = new OperationLog();
        BeanUtils.copyProperties(record, entity);
        entity.setCreateTime(LocalDateTime.now());
        if (entity.getOperationStatus() == null) {
            entity.setOperationStatus(STATUS_SUCCESS);
        }
        operationLogMapper.insert(entity);
    }

    @Override
    @DataScope
    public PageResult<OperationLogVO> page(OperationLogPageQuery query) {
        Page<OperationLog> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<OperationLog> result = operationLogMapper.selectPage(page, buildWrapper(query));
        List<OperationLogVO> records = result.getRecords().stream().map(this::toListVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @DataScope
    public void export(HttpServletResponse response, OperationLogPageQuery query) {
        List<OperationLog> logs = operationLogMapper.selectList(buildWrapper(query)
                .orderByDesc(OperationLog::getId)
                .last("LIMIT " + EXPORT_LIMIT));
        List<List<Object>> rows = new ArrayList<>(logs.size());
        for (OperationLog log : logs) {
            rows.add(List.of(
                    value(log.getId()),
                    value(log.getUsername()),
                    value(log.getModule()),
                    value(log.getOperation()),
                    value(log.getMethod()),
                    value(log.getRequestParams()),
                    value(log.getIpAddress()),
                    value(log.getCostTime()),
                    value(isSuccess(log.getOperationStatus()) ? "成功" : "失败"),
                    value(log.getErrorMsg()),
                    value(log.getCreateTime())));
        }
        String fileName = "操作日志_" + LocalDateTime.now().format(FILE_NAME_FORMATTER) + ".xlsx";
        ExcelExportUtil.write(response, fileName, "操作日志", EXPORT_HEADERS, rows);
        log.info("操作日志导出完成，条数={}", rows.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clean(LocalDateTime before, boolean all) {
        LocalDateTime cutoff = resolveCleanCutoff(before, all);
        int archived = operationLogMapper.archiveBefore(cutoff);
        int deleted = operationLogMapper.delete(new LambdaQueryWrapper<OperationLog>()
                .lt(OperationLog::getCreateTime, cutoff));
        log.info("操作日志已清理，模式={}，截止时间={}，归档条数={}，删除条数={}",
                all ? "全量" : "按时间", cutoff, archived, deleted);
        return deleted;
    }

    /**
     * 计算清理分界时间，并做安全护栏。
     *
     * <p><b>全量清理的关键设计</b>：分界取「本次请求开始时刻」而非 {@code now + 1}。
     * 本方法执行完会由 {@code @OperationLog} 切面写入一条审计日志（REQUIRES_NEW 事务），
     * 其 {@code create_time} 必然晚于请求开始时刻 —— 因此 {@code create_time < cutoff}
     * 不成立，这条审计日志<b>既不会被归档、也不会被自己删除</b>，
     * 从而满足「清理操作必须留痕」的要求。</p>
     *
     * <p><b>按时间清理的护栏</b>：要求保留至少 {@value #MIN_KEEP_DAYS} 天。
     * 日志是事后追责的唯一凭据，允许「把截止时间设成 5 分钟前」等于变相擦除痕迹。</p>
     */
    private LocalDateTime resolveCleanCutoff(LocalDateTime before, boolean all) {
        if (all) {
            return LocalDateTime.now();
        }
        if (before == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请指定清理截止时间，或选择全量清理");
        }
        LocalDateTime now = LocalDateTime.now();
        if (before.isAfter(now)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "清理截止时间不能晚于当前时间");
        }
        if (before.isAfter(now.minusDays(MIN_KEEP_DAYS))) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "为保证审计留痕，按时间清理时必须保留最近 " + MIN_KEEP_DAYS + " 天的日志");
        }
        return before;
    }

    // ---------- 内部方法 ----------

    private LambdaQueryWrapper<OperationLog> buildWrapper(OperationLogPageQuery query) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        // ① 先落可见范围（后端算出的唯一可信边界）
        LogScopeResolver.LogScope scope = logScopeResolver.resolve(QUERY_AUTHORITY);
        if (!scope.unlimited()) {
            // 前端 userId 只能在范围内收窄：交集为空时用「不可能命中」的条件，
            // 既不报错（不泄露该用户是否存在），也不返回越权数据
            if (query.getUserId() != null && scope.allows(query.getUserId())) {
                wrapper.eq(OperationLog::getUserId, query.getUserId());
            } else if (query.getUserId() != null) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(OperationLog::getUserId, scope.userIds());
            }
        } else if (query.getUserId() != null) {
            wrapper.eq(OperationLog::getUserId, query.getUserId());
        }
        // ② 再叠加业务筛选条件
        if (StringUtils.hasText(query.getUsername())) {
            wrapper.like(OperationLog::getUsername, query.getUsername());
        }
        if (StringUtils.hasText(query.getModule())) {
            wrapper.like(OperationLog::getModule, query.getModule());
        }
        if (query.getOperationStatus() != null) {
            wrapper.eq(OperationLog::getOperationStatus, query.getOperationStatus());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(OperationLog::getCreateTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(OperationLog::getCreateTime, query.getEndTime());
        }
        wrapper.orderByDesc(OperationLog::getId);
        return wrapper;
    }

    /** 列表只回传截断后的长文本，避免一条日志把整个响应撑大 */
    private OperationLogVO toListVO(OperationLog entity) {
        OperationLogVO vo = new OperationLogVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setRequestParams(truncate(entity.getRequestParams()));
        vo.setResponseResult(truncate(entity.getResponseResult()));
        vo.setErrorMsg(truncate(entity.getErrorMsg()));
        return vo;
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= LIST_TEXT_LIMIT) {
            return text;
        }
        return text.substring(0, LIST_TEXT_LIMIT) + "...";
    }

    private static boolean isSuccess(Integer status) {
        return status == null || status == STATUS_SUCCESS;
    }

    private static Object value(Object value) {
        return value == null ? "" : value;
    }
}
