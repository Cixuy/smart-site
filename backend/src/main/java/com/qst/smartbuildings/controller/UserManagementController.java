package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.dto.CreateUserRequest;
import com.qst.smartbuildings.dto.UpdateUserRequest;
import com.qst.smartbuildings.dto.UpdateUserStatusRequest;
import com.qst.smartbuildings.annotation.OperLog;
import com.qst.smartbuildings.exception.UserManagementException;
import com.qst.smartbuildings.service.UserManagementService;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.ResetPwdResult;
import com.qst.smartbuildings.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Arrays;
import java.util.List;

@Tag(name = "用户管理", description = "用户增删改查、状态调整与密码重置接口")
@RestController
@RequestMapping("/sys/user")
@Validated
public class UserManagementController {
    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @Operation(summary = "分页查询用户列表", description = "支持按用户名、真实姓名、手机号模糊查询以及状态筛选")
    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数必须大于0") @Max(value = 100, message = "每页条数不能超过100") long pageSize,
            @Parameter(description = "登录账号(模糊)") @RequestParam(required = false) String username,
            @Parameter(description = "真实姓名(模糊)") @RequestParam(required = false) String realName,
            @Parameter(description = "手机号") @RequestParam(required = false) String phone,
            @Parameter(description = "状态: 1-启用,0-禁用,2-锁定") @RequestParam(required = false) Integer status) {
        return Result.ok(userManagementService.page(pageNum, pageSize, username, realName, phone, status));
    }

    @Operation(summary = "查询用户详情", description = "返回用户信息及已分配的角色列表")
    @GetMapping("/{id}")
    public Result<UserVO> detail(@Parameter(description = "用户ID") @PathVariable Long id) {
        return Result.ok(userManagementService.detail(id));
    }

    @Operation(summary = "新增用户", description = "创建新用户并分配角色")
    @PostMapping
    @OperLog(title = "用户管理", operType = "新增")
    public Result<java.util.Map<String, Long>> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(java.util.Map.of("id", userManagementService.create(request)));
    }

    @Operation(summary = "修改用户", description = "修改用户基本信息及角色分配")
    @PutMapping
    @OperLog(title = "用户管理", operType = "修改")
    public Result<Void> update(@Valid @RequestBody UpdateUserRequest request) {
        userManagementService.update(request);
        return Result.ok();
    }

    @Operation(summary = "删除用户(批量)", description = "逻辑删除，已删除的用户不可恢复")
    @DeleteMapping("/{ids}")
    @OperLog(title = "用户管理", operType = "删除")
    public Result<Void> delete(@Parameter(description = "用户ID, 多个用逗号分隔") @PathVariable String ids) {
        List<Long> userIds;
        try {
            userIds = Arrays.stream(ids.split(",")).map(String::trim).filter(value -> !value.isEmpty()).map(Long::valueOf).toList();
        } catch (NumberFormatException ex) {
            throw new UserManagementException("用户ID格式不正确");
        }
        userManagementService.delete(userIds);
        return Result.ok();
    }

    @Operation(summary = "修改用户状态", description = "启用/禁用/解锁用户")
    @PutMapping("/{id}/status")
    @OperLog(title = "用户管理", operType = "修改")
    public Result<Void> changeStatus(@Parameter(description = "用户ID") @PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        userManagementService.changeStatus(id, request.statusValue());
        return Result.ok();
    }

    @Operation(summary = "重置密码", description = "管理员重置用户密码，系统生成6位随机密码并返回，同时标记该密码需在首次登录时修改")
    @PutMapping("/{id}/resetPwd")
    @OperLog(title = "用户管理", operType = "重置密码")
    public Result<ResetPwdResult> resetPassword(@Parameter(description = "用户ID") @PathVariable Long id) {
        return Result.ok(userManagementService.resetPassword(id));
    }

    @ExceptionHandler(UserManagementException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleUserManagementException(UserManagementException exception) {
        return Result.error(400, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException exception) {
        FieldError error = exception.getBindingResult().getFieldError();
        return Result.error(400, error == null ? "请求参数不正确" : error.getDefaultMessage());
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
            case "status" -> "用户状态必须是数字";
            case "id" -> "用户ID必须是数字";
            default -> "请求参数格式不正确：" + parameter;
        };
        return Result.error(400, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException exception) {
        return Result.error(400, "缺少请求参数：" + exception.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return Result.error(400, "请求体格式不正确，请检查JSON内容和字段类型");
    }
}