package com.kellen.oa_system.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>职责：把散落在各层的异常统一转换成 {@link Result}，避免 Controller 里写 try-catch。
 * 安全约束：任何对外响应都不得包含堆栈信息或内部类名，详细原因只写日志。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期，按 info 级别记录即可，不打堆栈 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常 code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody + @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return Result.fail(ErrorCode.BAD_REQUEST, detail);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", detail);
        return Result.fail(ErrorCode.BAD_REQUEST, detail);
    }

    /** 方法参数上的 @Validated 校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", detail);
        return Result.fail(ErrorCode.BAD_REQUEST, detail);
    }

    /** 请求体 JSON 解析失败 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST, "请求体格式错误或无法解析");
    }

    /** 认证失败（Controller 层抛出；过滤器链的由 AuthenticationEntryPoint 处理） */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return Result.fail(ErrorCode.UNAUTHORIZED);
    }

    /** 授权失败（方法级鉴权抛出；过滤器链的由 AccessDeniedHandler 处理） */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("无权限访问: {}", e.getMessage());
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    /** 上传文件超过 multipart 限制：若不单独拦截会落进兜底分支返回 5000，前端拿不到可读原因 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return Result.fail(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    /**
     * 缺少必需的查询参数（如 {@code @RequestParam} 未传）。
     *
     * <p>该异常在方法体执行前由 Spring 抛出，若落到兜底分支会变成「系统内部错误 5000」，
     * 把调用方的传参问题误判成服务端故障，因此单独拦截。</p>
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        String detail = "缺少必填参数: " + e.getParameterName();
        log.warn("请求参数缺失: {}", detail);
        return Result.fail(ErrorCode.BAD_REQUEST, detail);
    }

    /** 参数类型/格式转换失败（如日期参数传了非日期字符串） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String detail = "参数格式不正确: " + e.getName();
        log.warn("请求参数格式错误: {}, 实际值={}", e.getName(), e.getValue());
        return Result.fail(ErrorCode.BAD_REQUEST, detail);
    }

    /** 请求方法不被支持（如把 POST 接口用 GET 调用） */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方式不被允许: {}", e.getMessage());
        return Result.fail(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 静态资源/接口不存在：静默返回 404，不打堆栈（爬虫探测属常态） */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return Result.fail(ErrorCode.NOT_FOUND);
    }

    /**
     * 唯一键 / 唯一索引冲突：并发重复提交的落点（D10）。
     *
     * <p>服务层普遍是「先查后插」——两个请求同时通过预检、同时 INSERT 时，
     * 数据库唯一索引只放行一个，另一个抛 {@link DuplicateKeyException}。
     * 若落到兜底分支就变成「系统内部错误 5000」，用户以为系统坏了、继续点，
     * 于是重复提交越积越多。这里单独拦截，返回可解释的 5009。</p>
     *
     * <p>只记 warn 不打堆栈：这是客户端并发行为，不是服务端故障。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突（疑似并发重复提交）: {}", rootMessage(e));
        return Result.fail(ErrorCode.DUPLICATE_SUBMIT);
    }

    /**
     * 其余数据完整性冲突（非空、长度、外键等）。
     *
     * <p>绝大多数是「提交的数据不合规」，属于客户端问题；但如果驱动打包成
     * `DataIntegrityViolationException` 且原因仍是唯一键冲突，仍按 5009 处理。</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String cause = rootMessage(e);
        if (cause != null && (cause.contains("Duplicate entry") || cause.contains("uk_")
                || cause.toLowerCase().contains("unique"))) {
            log.warn("唯一键冲突（疑似并发重复提交）: {}", cause);
            return Result.fail(ErrorCode.DUPLICATE_SUBMIT);
        }
        log.warn("数据完整性约束不满足: {}", cause);
        return Result.fail(ErrorCode.BAD_REQUEST, "提交的数据不符合约束，请检查必填项、长度与格式");
    }

    /** 兜底：未知异常一律转成系统错误，完整堆栈只进日志 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未处理异常: {}", e.getMessage(), e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 取最内层原因的消息，且**只截取到换行/长度上限**。
     *
     * <p>JDBC 的重复键消息会带完整 SQL 与索引名（如
     * `Duplicate entry 'a-4-2099-1' for key 'oa_leave_policy.uk_policy_city_type_year'`），
     * 直接进日志可以，但绝不能透给客户端——所以只用于日志与内部判定。</p>
     */
    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        if (msg == null) {
            return null;
        }
        int nl = msg.indexOf('\n');
        String head = nl > 0 ? msg.substring(0, nl) : msg;
        return head.length() > 500 ? head.substring(0, 500) + "..." : head;
    }

    private String formatFieldError(FieldError fieldError) {
        String field = fieldError.getField();
        String msg = fieldError.getDefaultMessage();
        // Spring 类型转换失败时会自动生成含 java.lang.* 内部类名的默认消息
        // （如 "Failed to convert property value of type 'java.lang.String' to required type 'java.lang.Long'"），
        // 直接透传给客户端会泄露服务端内部类型信息，需脱敏为通用提示。
        if (msg != null && (msg.contains("java.lang.") || msg.contains("Failed to convert")
                || msg.contains("required type") || msg.contains("PropertyEditor"))) {
            return field + ": 格式不正确";
        }
        return field + ": " + (msg == null ? "格式不正确" : msg);
    }
}
