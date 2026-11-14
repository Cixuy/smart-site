package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.exception.LogException;
import com.qst.smartbuildings.service.LogService;
import com.qst.smartbuildings.vo.LoginLogVO;
import com.qst.smartbuildings.vo.OperLogVO;
import com.qst.smartbuildings.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 日志管理
 */
@Tag(name = "日志管理", description = "登录日志、操作日志查询接口")
@RestController
@RequestMapping("/sys/log")
@Validated
public class SysLogController {

    private final LogService logService;

    public SysLogController(LogService logService) {
        this.logService = logService;
    }

    /**
     * 分页查询登录日志
     */
    @Operation(summary = "分页查询登录日志")
    @GetMapping("/login/page")
    public Result<PageResult<LoginLogVO>> pageLoginLogs(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须大于0") @Max(value = 100, message = "每页条数不能超过100") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String loginIp,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok(logService.pageLoginLogs(pageNum, pageSize, username, status, loginIp, beginTime, endTime));
    }

    /**
     * 分页查询操作日志
     */
    @Operation(summary = "分页查询操作日志")
    @GetMapping("/oper/page")
    public Result<PageResult<OperLogVO>> pageOperLogs(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须大于0") @Max(value = 100, message = "每页条数不能超过100") long pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String operType,
            @RequestParam(required = false) String operByName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {
        return Result.ok(logService.pageOperLogs(pageNum, pageSize, title, operType, operByName, status, beginTime, endTime));
    }

    /**
     * 查询操作日志详情
     */
    @Operation(summary = "查询操作日志详情")
    @GetMapping("/oper/{id}")
    public Result<OperLogVO> getOperLog(@PathVariable Long id) {
        return Result.ok(logService.getOperLogDetail(id));
    }

    @ExceptionHandler(LogException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleLogException(LogException exception) {
        return Result.error(400, exception.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("请求参数不正确");
        return Result.error(400, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String parameter = exception.getName();
        String message = switch (parameter) {
            case "pageNum" -> "页码必须是正整数";
            case "pageSize" -> "每页条数必须是正整数";
            case "status" -> "状态参数必须是数字";
            case "id" -> "日志ID必须是数字";
            default -> "请求参数格式不正确：" + parameter;
        };
        return Result.error(400, message);
    }
}