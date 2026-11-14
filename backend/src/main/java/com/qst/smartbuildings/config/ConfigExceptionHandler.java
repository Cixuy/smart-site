package com.qst.smartbuildings.config;

import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.exception.DeviceTypeException;
import com.qst.smartbuildings.exception.EquipmentException;
import com.qst.smartbuildings.exception.NoLoginException;
import com.qst.smartbuildings.exception.MonitoringException;
import com.qst.smartbuildings.exception.SceneException;
import com.qst.smartbuildings.exception.SprayException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 全局异常处理器
@RestControllerAdvice
public class ConfigExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ConfigExceptionHandler.class);

    // 处理认证失败相关异常（如密码错误）
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result<Void>> handleBadCredentialsException(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, "用户名或密码错误"));
    }

    // 处理用户不存在异常
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Result<Void>> handleUsernameNotFoundException(UsernameNotFoundException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, "用户不存在"));
    }
    // 处理用户不存在异常
    @ExceptionHandler(EquipmentException.class)
    public ResponseEntity<Result<Void>> handleEquipmentLocationException(EquipmentException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "设备异常，原因：" + e.getMsg()));
    }

    @ExceptionHandler(DeviceTypeException.class)
    public ResponseEntity<Result<Void>> handleDeviceTypeException(DeviceTypeException e) {
        return ResponseEntity.badRequest().body(Result.error(400, e.getMessage()));
    }

    @ExceptionHandler(MonitoringException.class)
    public ResponseEntity<Result<Void>> handleMonitoringException(MonitoringException e) {
        return ResponseEntity.badRequest().body(Result.error(400, e.getMessage()));
    }

    @ExceptionHandler(SprayException.class)
    public ResponseEntity<Result<Void>> handleSprayException(SprayException e) {
        HttpStatus status = switch (e.getCode()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 401 -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMsg()));
    }

    @ExceptionHandler(SceneException.class)
    public ResponseEntity<Result<Void>> handleSceneException(SceneException e) {
        HttpStatus status = switch (e.getCode()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMsg()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<Result<Void>> handleValidationException(Exception e) {
        return ResponseEntity.badRequest().body(Result.error(400, buildValidationMessage(e)));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class, HttpMessageNotReadableException.class, TypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleParameterTypeException(Exception e) {
        return ResponseEntity.badRequest().body(Result.error(400, buildParameterMessage(e)));
    }

    @ExceptionHandler({NoLoginException.class, AuthenticationException.class})
    public ResponseEntity<Result<Void>> handleAuthenticationProblem(Exception e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(401, "请先登录"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(403, "无权操作"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccessException(DataAccessException e) {
        log.error("数据库操作失败", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "数据库操作失败，请稍后重试"));
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<Result<Void>> handleRedisConnectionFailure(RedisConnectionFailureException e) {
        log.error("Redis认证服务连接失败", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.error(503, "认证服务不可用，请确认Redis SSH隧道已建立"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "系统异常，请联系管理员"));
    }

    private String buildValidationMessage(Exception e) {
        if (e instanceof MethodArgumentNotValidException invalid) {
            return invalid.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(err -> "请求参数错误：" + err.getField() + " " + err.getDefaultMessage())
                    .orElse("请求参数错误");
        }
        if (e instanceof ConstraintViolationException violation) {
            return violation.getConstraintViolations().stream()
                    .findFirst()
                    .map(v -> "请求参数错误：" + v.getPropertyPath() + " " + v.getMessage())
                    .orElse("请求参数错误");
        }
        return "请求参数错误";
    }

    private String buildParameterMessage(Exception e) {
        if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            String param = mismatch.getName();
            String type = mismatch.getRequiredType() == null ? "目标" : mismatch.getRequiredType().getSimpleName();
            return "请求参数错误：" + param + " 应为 " + type + " 类型";
        }
        if (e instanceof MissingServletRequestParameterException missing) {
            return "请求参数错误：缺少参数 " + missing.getParameterName();
        }
        if (e instanceof HttpMessageNotReadableException) {
            return "请求体格式错误：请检查数字字段是否传入了字母或非法内容";
        }
        if (e instanceof TypeMismatchException mismatch) {
            return "请求参数错误：" + mismatch.getPropertyName() + " 类型不正确";
        }
        return "请求参数错误";
    }

}
