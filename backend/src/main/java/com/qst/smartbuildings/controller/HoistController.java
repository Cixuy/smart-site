package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.dto.HoistThresholdUpdateRequest;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.exception.HoistException;
import com.qst.smartbuildings.service.HoistService;
import com.qst.smartbuildings.vo.HoistRealtimeVO;
import com.qst.smartbuildings.vo.HoistThresholdVO;
import com.qst.smartbuildings.vo.HoistWorkRecordVO;
import com.qst.smartbuildings.vo.HoistWorkStatisticsVO;
import com.qst.smartbuildings.vo.PageListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 升降机监控
 */
@Tag(name = "升降机监控", description = "升降机实时数据、历史曲线、联锁告警、阈值配置、作业记录接口")
@RestController
@RequestMapping("/hoist")
@Validated
public class HoistController {

    private final HoistService hoistService;

    public HoistController(HoistService hoistService) {
        this.hoistService = hoistService;
    }

    /**
     * 查询升降机最新实时数据
     */
    @Operation(summary = "查询升降机最新实时数据")
    @GetMapping("/realtime/latest")
    public Result<Map<String, List<HoistRealtimeVO>>> latest(
            @RequestParam(required = false) String deviceId) {
        return Result.ok(Map.of("list", hoistService.latest(deviceId)));
    }

    /**
     * 查询升降机历史数据（时序曲线）
     */
    @Operation(summary = "查询升降机历史数据（时序曲线）")
    @GetMapping("/realtime/history")
    public Result<PageListVO<HoistRealtimeVO>> history(
            @RequestParam String deviceId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "100") @Min(value = 1, message = "每页条数必须大于0")
            @Max(value = 1000, message = "每页条数不能超过1000") long pageSize) {
        return Result.ok(hoistService.history(deviceId, startTime, endTime, pageNum, pageSize));
    }

    /**
     * 查询升降机联锁告警列表
     */
    @Operation(summary = "查询升降机联锁告警列表")
    @GetMapping("/realtime/interlock-alarms")
    public Result<PageListVO<HoistRealtimeVO>> interlockAlarms(
            @RequestParam(required = false) Integer interlockAlarm,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数必须大于0")
            @Max(value = 100, message = "每页条数不能超过100") long pageSize) {
        return Result.ok(hoistService.interlockAlarms(interlockAlarm, deviceId, startTime, endTime, pageNum, pageSize));
    }

    /**
     * 查询升降机各指标阈值配置
     */
    @Operation(summary = "查询升降机各指标阈值配置")
    @GetMapping("/thresholds")
    public Result<PageListVO<HoistThresholdVO>> thresholds(
            @RequestParam(required = false) String deviceId) {
        return Result.ok(hoistService.thresholds(deviceId));
    }

    /**
     * 修改升降机阈值配置
     */
    @Operation(summary = "修改升降机阈值配置")
    @PutMapping("/thresholds")
    public Result<Map<String, Boolean>> updateThresholds(
            @Valid @RequestBody HoistThresholdUpdateRequest request) {
        hoistService.updateThresholds(request);
        return Result.ok(Map.of("success", true));
    }

    /**
     * 查询升降机作业记录列表
     */
    @Operation(summary = "查询升降机作业记录列表")
    @GetMapping("/work-records")
    public Result<PageListVO<HoistWorkRecordVO>> workRecords(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String floorRange,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数必须大于0")
            @Max(value = 100, message = "每页条数不能超过100") long pageSize) {
        return Result.ok(hoistService.workRecords(deviceId, floorRange, startTime, endTime, pageNum, pageSize));
    }

    /**
     * 获取升降机作业统计
     */
    @Operation(summary = "获取升降机作业统计")
    @GetMapping("/work-records/statistics")
    public Result<HoistWorkStatisticsVO> workStatistics(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String statDate) {
        return Result.ok(hoistService.workStatistics(deviceId, statDate));
    }

    @ExceptionHandler(HoistException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHoistException(HoistException exception) {
        return Result.error(400, exception.getMessage());
    }
}