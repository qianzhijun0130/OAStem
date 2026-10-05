package com.kellen.oa_system.system.log;

import com.kellen.oa_system.common.annotation.OperationLog;
import com.kellen.oa_system.common.util.IpUtil;
import com.kellen.oa_system.security.LoginUser;
import com.kellen.oa_system.system.dto.OperationLogRecord;
import com.kellen.oa_system.system.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 操作日志切面。
 *
 * <p>拦截标注了 {@link OperationLog} 的方法，采集「谁、在什么环境、做了什么、结果如何」，
 * 落库交给 {@link OperationLogService}。</p>
 *
 * <p><b>三条硬约束</b>：</p>
 * <ol>
 *   <li>日志绝不能影响业务：整个采集过程包在 try-catch 里，失败只打 WARN。</li>
 *   <li>日志绝不能拖慢业务：参数序列化失败、SpEL 解析失败都静默降级，不做重试。</li>
 *   <li>日志绝不能泄漏密码：参数序列化后按字段名做一次正则脱敏。</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILURE = 0;

    /** 落库文本上限，避免超长报文把 TEXT 字段撑爆 */
    private static final int MAX_TEXT_LENGTH = 2000;

    /** 敏感字段名（序列化后按 "字段": "值" 形式替换） */
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(\"(?:password|oldPassword|newPassword|confirmPassword|token|secret|accessKey)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    /** 无需序列化的参数类型：Servlet 对象与文件流既没信息量又容易抛异常 */
    private static final List<Class<?>> IGNORED_ARG_TYPES = List.of(
            HttpServletRequest.class,
            jakarta.servlet.http.HttpServletResponse.class,
            MultipartFile.class,
            MultipartFile[].class,
            org.springframework.validation.BindingResult.class);

    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
    private static final TemplateParserContext TEMPLATE_CONTEXT = new TemplateParserContext();
    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
            new DefaultParameterNameDiscoverer();

    /**
     * 宽松类型转换器：把任意表达式结果按 {@code String.valueOf} 转成字符串。
     *
     * <p>SpEL 默认的 {@code StandardTypeConverter} 依赖 {@code DefaultConversionService}，
     * 它只认显式注册的类型转换器；{@code Long -> String} 能转，但
     * {@code LocalDateTime -> String} 会抛
     * {@code ConverterNotFoundException}，导致 {@code #{#before}} 这类日期参数
     * 解析失败并被静默降级成原始模板（日志里留下 {@code #{#before}} 字面量）。</p>
     */
    private static final TypeConverter LENIENT_TYPE_CONVERTER = createLenientTypeConverter();

    private static TypeConverter createLenientTypeConverter() {
        DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.addConverter(Object.class, String.class, (Converter<Object, String>) String::valueOf);
        return new StandardTypeConverter(conversionService);
    }

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startAt = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            // 记下异常后原样抛出：切面只做观察者，不改变业务语义
            error = throwable;
            throw throwable;
        } finally {
            collect(joinPoint, operationLog, result, error, System.currentTimeMillis() - startAt);
        }
    }

    /** 采集并落库；任何异常都在此消化，不向外传播 */
    private void collect(ProceedingJoinPoint joinPoint,
                         OperationLog operationLog,
                         Object result,
                         Throwable error,
                         long costTime) {
        try {
            OperationLogRecord record = new OperationLogRecord();
            fillOperator(record);
            record.setModule(operationLog.module());
            record.setOperation(resolveOperation(operationLog.operation(), joinPoint));
            record.setMethod(joinPoint.getSignature().getDeclaringTypeName()
                    + "." + joinPoint.getSignature().getName());
            if (operationLog.saveParams()) {
                record.setRequestParams(truncate(toJson(serializableArgs(joinPoint.getArgs()))));
            }
            if (operationLog.saveResult() && error == null) {
                record.setResponseResult(truncate(toJson(result)));
            }
            fillRequestEnv(record);
            record.setCostTime((int) costTime);
            record.setOperationStatus(error == null ? STATUS_SUCCESS : STATUS_FAILURE);
            if (error != null) {
                record.setErrorMsg(truncate(error.getMessage()));
            }
            operationLogService.save(record);
        } catch (Exception e) {
            // 日志失败不能影响已完成的业务，这里只告警
            log.warn("操作日志采集失败 module={} error={}", operationLog.module(), e.getMessage());
        }
    }

    private void fillOperator(OperationLogRecord record) {
        LoginUser loginUser = currentLoginUser();
        if (loginUser != null) {
            record.setUserId(loginUser.getUserId());
            record.setUsername(loginUser.getUsername());
        }
    }

    private void fillRequestEnv(OperationLogRecord record) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        record.setIpAddress(IpUtil.getClientIp(request));
        // location 需要外部 IP 库解析，暂不实现，保留字段以便后续接入
    }

    /**
     * 解析 {@code operation} 里的 {@code #{...}} 表达式。
     *
     * <p>解析失败时原样返回模板，而不是抛异常——日志描述只是可读性加分项，
     * 不值得为它中断业务。</p>
     */
    private String resolveOperation(String template, ProceedingJoinPoint joinPoint) {
        if (!StringUtils.hasText(template) || !template.contains("#{")) {
            return template;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    null, signature.getMethod(), joinPoint.getArgs(), PARAMETER_NAME_DISCOVERER);
            context.setTypeConverter(LENIENT_TYPE_CONVERTER);
            String resolved = EXPRESSION_PARSER
                    .parseExpression(template, TEMPLATE_CONTEXT)
                    .getValue(context, String.class);
            return StringUtils.hasText(resolved) ? resolved : template;
        } catch (Exception e) {
            // 注解写错属于开发期问题，保留原始模板保证日志仍可用，但把原因留在 DEBUG 便于定位
            log.debug("操作描述解析失败，保留原始模板: template={}, error={}", template, e.getMessage());
            return template;
        }
    }

    private List<Object> serializableArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return List.of();
        }
        return Arrays.stream(args)
                .filter(arg -> arg != null && !isIgnored(arg.getClass()))
                .toList();
    }

    private boolean isIgnored(Class<?> type) {
        return IGNORED_ARG_TYPES.stream().anyMatch(ignored -> ignored.isAssignableFrom(type));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            return SENSITIVE_FIELD_PATTERN.matcher(json).replaceAll("$1\"***\"");
        } catch (Exception e) {
            // 序列化失败（如存在不可序列化的字段）时退化为基础描述，不影响主流程
            return value.toString();
        }
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH) + "...";
    }

    private static LoginUser currentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }
}
