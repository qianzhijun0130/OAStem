package com.kellen.oa_system.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kellen.oa_system.common.BizException;
import com.kellen.oa_system.common.ErrorCode;
import com.kellen.oa_system.common.PageResult;
import com.kellen.oa_system.common.util.ExcelExportUtil;
import com.kellen.oa_system.system.dto.LoginLogPageQuery;
import com.kellen.oa_system.system.entity.LoginLog;
import com.kellen.oa_system.system.mapper.LoginLogMapper;
import com.kellen.oa_system.common.annotation.DataScope;
import com.kellen.oa_system.system.service.LoginLogService;
import com.kellen.oa_system.system.service.support.LogScopeResolver;
import com.kellen.oa_system.system.vo.LoginLogVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录日志服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {

    /** 导出上限，超过应改用离线导出 */
    private static final int EXPORT_LIMIT = 10000;

    private static final int LOGIN_TYPE_PASSWORD = 1;
    private static final int STATUS_SUCCESS = 1;

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final List<String> EXPORT_HEADERS = List.of(
            "日志ID", "登录账号", "登录类型", "IP地址", "登录地点",
            "浏览器", "操作系统", "状态", "失败原因", "登录时间");

    /** 访问本类日志所需的功能权限码：无此权限者只能看自己的日志 */
    private static final String QUERY_AUTHORITY = "system:login-log:query";

    /** 按时间清理时必须保留的最少天数 */
    private static final int MIN_KEEP_DAYS = 1;

    private final LoginLogMapper loginLogMapper;
    private final LogScopeResolver logScopeResolver;

    @Override
    @DataScope
    public PageResult<LoginLogVO> page(LoginLogPageQuery query) {
        Page<LoginLog> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<LoginLog> result = loginLogMapper.selectPage(page, buildWrapper(query));
        List<LoginLogVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @DataScope
    public void export(HttpServletResponse response, LoginLogPageQuery query) {
        List<LoginLog> logs = loginLogMapper.selectList(buildWrapper(query)
                .orderByDesc(LoginLog::getId)
                .last("LIMIT " + EXPORT_LIMIT));
        List<List<Object>> rows = new ArrayList<>(logs.size());
        for (LoginLog logItem : logs) {
            rows.add(List.of(
                    value(logItem.getId()),
                    value(logItem.getUsername()),
                    value(loginTypeText(logItem.getLoginType())),
                    value(logItem.getIpAddress()),
                    value(logItem.getLocation()),
                    value(logItem.getBrowser()),
                    value(logItem.getOs()),
                    value(isSuccess(logItem.getLoginStatus()) ? "成功" : "失败"),
                    value(logItem.getFailReason()),
                    value(logItem.getCreateTime())));
        }
        String fileName = "登录日志_" + LocalDateTime.now().format(FILE_NAME_FORMATTER) + ".xlsx";
        ExcelExportUtil.write(response, fileName, "登录日志", EXPORT_HEADERS, rows);
        log.info("登录日志导出完成，条数={}", rows.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clean(LocalDateTime before, boolean all) {
        LocalDateTime cutoff = resolveCleanCutoff(before, all);
        int archived = loginLogMapper.archiveBefore(cutoff);
        int deleted = loginLogMapper.delete(new LambdaQueryWrapper<LoginLog>()
                .lt(LoginLog::getCreateTime, cutoff));
        log.info("登录日志已清理，模式={}，截止时间={}，归档条数={}，删除条数={}",
                all ? "全量" : "按时间", cutoff, archived, deleted);
        return deleted;
    }

    /** 分界时间计算与护栏口径同操作日志，详见 {@code OperationLogServiceImpl#resolveCleanCutoff} */
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

    private LambdaQueryWrapper<LoginLog> buildWrapper(LoginLogPageQuery query) {
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        // ① 先落可见范围（后端算出的唯一可信边界）
        LogScopeResolver.LogScope scope = logScopeResolver.resolve(QUERY_AUTHORITY);
        if (!scope.unlimited()) {
            if (query.getUserId() != null && scope.allows(query.getUserId())) {
                wrapper.eq(LoginLog::getUserId, query.getUserId());
            } else if (query.getUserId() != null) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(LoginLog::getUserId, scope.userIds());
            }
        } else if (query.getUserId() != null) {
            wrapper.eq(LoginLog::getUserId, query.getUserId());
        }
        // ② 再叠加业务筛选条件
        if (StringUtils.hasText(query.getUsername())) {
            wrapper.like(LoginLog::getUsername, query.getUsername());
        }
        if (StringUtils.hasText(query.getIpAddress())) {
            wrapper.like(LoginLog::getIpAddress, query.getIpAddress());
        }
        if (query.getLoginStatus() != null) {
            wrapper.eq(LoginLog::getLoginStatus, query.getLoginStatus());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(LoginLog::getCreateTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(LoginLog::getCreateTime, query.getEndTime());
        }
        wrapper.orderByDesc(LoginLog::getId);
        return wrapper;
    }

    private LoginLogVO toVO(LoginLog entity) {
        LoginLogVO vo = new LoginLogVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private static String loginTypeText(Integer loginType) {
        return loginType != null && loginType == LOGIN_TYPE_PASSWORD ? "账号密码登录" : "其他";
    }

    private static boolean isSuccess(Integer status) {
        return status == null || status == STATUS_SUCCESS;
    }

    private static Object value(Object value) {
        return value == null ? "" : value;
    }
}
