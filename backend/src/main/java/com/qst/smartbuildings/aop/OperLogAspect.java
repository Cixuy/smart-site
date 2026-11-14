package com.qst.smartbuildings.aop;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.filter.Filter;
import com.alibaba.fastjson2.filter.SimplePropertyPreFilter;
import com.qst.smartbuildings.annotation.OperLog;
import com.qst.smartbuildings.common.AuthPrincipal;
import com.qst.smartbuildings.common.UserContext;
import com.qst.smartbuildings.service.LogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 操作日志切面。
 * <p>拦截标注 {@link OperLog} 的 Controller 方法，记录操作人、请求参数、执行状态、
 * 错误信息并写入 sys_oper_log。日志记录自身发生异常时不影响业务执行。</p>
 */
@Aspect
@Component
public class OperLogAspect {

    private static final String[] SENSITIVE_FIELDS = {"password", "oldPassword", "newPassword", "accessToken"};
    private static final int MAX_PARAM_LENGTH = 4000;

    private final LogService logService;

    public OperLogAspect(LogService logService) {
        this.logService = logService;
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        AuthPrincipal principal = UserContext.get();
        Long operById = principal == null ? null : principal.getUserId();
        String operByName = principal == null ? null :
                (StringUtils.hasText(principal.getRealName()) ? principal.getRealName() : principal.getUsername());
        String operParam = serializeArgs(joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            logService.recordOperLog(operLog.title(), operLog.operType(), operById, operByName,
                    operParam, 0, null);
            return result;
        } catch (Throwable ex) {
            logService.recordOperLog(operLog.title(), operLog.operType(), operById, operByName,
                    operParam, 1, StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        SimplePropertyPreFilter filter = new SimplePropertyPreFilter();
        for (String field : SENSITIVE_FIELDS) {
            filter.getExcludes().add(field);
        }
        try {
            if (args.length == 1) {
                return truncate(JSON.toJSONString(asString(args[0]), (Filter) filter));
            }
            Object[] copy = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                copy[i] = asString(args[i]);
            }
            return truncate(JSON.toJSONString(copy, filter));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 请求上下文/大对象不进入 JSON 参数，避免序列化失败或超长
     */
    private Object asString(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof jakarta.servlet.ServletRequest
                || arg instanceof jakarta.servlet.ServletResponse
                || arg instanceof org.springframework.web.multipart.MultipartFile) {
            return null;
        }
        return arg;
    }

    private String truncate(String json) {
        if (json == null || json.length() <= MAX_PARAM_LENGTH) {
            return json;
        }
        return json.substring(0, MAX_PARAM_LENGTH);
    }
}