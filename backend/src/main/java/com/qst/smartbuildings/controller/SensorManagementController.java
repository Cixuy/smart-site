package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.annotation.OperLog;
import com.qst.smartbuildings.dto.SensorCreateRequest;
import com.qst.smartbuildings.dto.SensorStatusRequest;
import com.qst.smartbuildings.dto.SensorDataPushRequest;
import com.qst.smartbuildings.dto.SensorUpdateRequest;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.service.SensorManagementService;
import com.qst.smartbuildings.service.EnvMonitorService;
import com.qst.smartbuildings.exception.SensorNotFoundException;
import com.qst.smartbuildings.exception.SensorParentNotFoundException;
import org.springframework.http.HttpStatus;
import com.qst.smartbuildings.vo.SensorPageVO;
import com.qst.smartbuildings.vo.SensorVO;
import com.qst.smartbuildings.vo.EnvDataPushResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Arrays;

@RestController
@RequestMapping("/asset/sensor")
@Validated
@Tag(name = "传感器管理", description = "设备或环境监测点传感器配置、状态及数据上报")
public class SensorManagementController {
    private final SensorManagementService service;
    private final EnvMonitorService envMonitorService;
    public SensorManagementController(SensorManagementService service, EnvMonitorService envMonitorService) { this.service = service; this.envMonitorService = envMonitorService; }
    @Operation(summary = "分页查询传感器列表", description = "按名称、父级、监测类型和状态筛选传感器")
    @GetMapping("/page")
    public Result<SensorPageVO> page(@RequestParam(defaultValue = "1") @Min(1) long pageNum,
                                     @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
                                     @RequestParam(required = false) String sensorName,
                                     @RequestParam(required = false) Long parentId,
                                     @RequestParam(required = false) @Min(0) @Max(3) Integer parentType,
                                     @RequestParam(required = false) String monitorType,
                                     @RequestParam(required = false) Integer status) {
        return Result.ok(service.page(pageNum, pageSize, sensorName, parentId, parentType, monitorType, status));
    }
    @Operation(summary = "查询传感器详情")
    @GetMapping("/{id}") public Result<SensorVO> detail(@PathVariable Long id) { return Result.ok(service.detail(id)); }
    @Operation(summary = "新增传感器")
    @PostMapping public Result<java.util.Map<String, Long>> create(@Valid @RequestBody SensorCreateRequest request) { return Result.ok(java.util.Map.of("id", service.create(request))); }
    @Operation(summary = "修改传感器")
    @PutMapping public Result<Void> update(@Valid @RequestBody SensorUpdateRequest request) { service.update(request); return Result.ok(); }
    @Operation(summary = "批量删除传感器")
    @DeleteMapping("/{ids}") public Result<Void> delete(@PathVariable String ids) { service.delete(Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList()); return Result.ok(); }
    @Operation(summary = "修改传感器运行状态")
    @PutMapping("/{id}/status") public Result<Void> status(@PathVariable Long id, @Valid @RequestBody SensorStatusRequest request) { service.changeStatus(id, request.getStatus()); return Result.ok(); }
    @ExceptionHandler(SensorNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(SensorNotFoundException exception) {
        return Result.error(404, exception.getMessage());
    }

    @ExceptionHandler(SensorParentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleParentNotFound(SensorParentNotFoundException exception) {
        return Result.error(404, exception.getMessage());
    }

    @Operation(summary = "传感器数据上报")
    @PostMapping("/data/push") public Result<EnvDataPushResult> pushData(@Valid @RequestBody SensorDataPushRequest request) { return Result.ok(envMonitorService.pushSensorData(request)); }
}
