package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.dto.TowerCraneThresholdUpdateRequest;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.exception.TowerCraneException;
import com.qst.smartbuildings.service.TowerCraneService;
import com.qst.smartbuildings.vo.PageListVO;
import com.qst.smartbuildings.vo.TowerCraneRealtimeVO;
import com.qst.smartbuildings.vo.TowerCraneThresholdVO;
import com.qst.smartbuildings.vo.TowerCraneWorkRecordVO;
import com.qst.smartbuildings.vo.TowerCraneWorkStatisticsVO;
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
 * 塔吊监控
 */
@Tag(name = "塔吊监控", description = "塔吊实时数据、历史曲线、告警、阈值配置、作业记录接口")
@RestController
@RequestMapping("/tower-crane")
@Validated
public class TowerCraneController {

    private final TowerCraneService towerCraneService;

    public TowerCraneController(TowerCraneService towerCraneService) {
        this.towerCraneService = towerCraneService;
    }

    /**
     * 查询塔吊最新实时数据
     */
    @Operation(summary = "查询塔吊最新实时数据")
    @GetMapping("/realtime/latest")
    public Result<Map<String, List<TowerCraneRealtimeVO>>> latest(
            @RequestParam(required = false) String deviceId) {
        return Result.ok(Map.of("list", towerCraneService.latest(deviceId)));
    }

    /**
     * 查询塔吊历史数据（时序曲线）
     */
    @Operation(summary = "查询塔吊历史数据（时序曲线）")
    @GetMapping("/realtime/history")
    public Result<PageListVO<TowerCraneRealtimeVO>> history(
            @RequestParam String deviceId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "100") @Min(value = 1, message = "每页条数必须大于0")
            @Max(value = 1000, message = "每页条数不能超过1000") long pageSize) {
        return Result.ok(towerCraneService.history(deviceId, startTime, endTime, pageNum, pageSize));
    }

    /**
     * 查询塔吊告警列表
     */
    @Operation(summary = "查询塔吊告警列表")
    @GetMapping("/realtime/alarms")
    public Result<PageListVO<TowerCraneRealtimeVO>> alarms(
            @RequestParam(required = false) Integer alarmStatus,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数必须大于0")
            @Max(value = 100, message = "每页条数不能超过100") long pageSize) {
        return Result.ok(towerCraneService.alarms(alarmStatus, deviceId, startTime, endTime, pageNum, pageSize));
    }

    /**
     * 查询塔吊各指标阈值配置
     */
    @Operation(summary = "查询塔吊各指标阈值配置")
    @GetMapping("/thresholds")
    public Result<PageListVO<TowerCraneThresholdVO>> thresholds(
            @RequestParam(required = false) String deviceId) {
        return Result.ok(towerCraneService.thresholds(deviceId));
    }

    /**
     * 修改塔吊阈值配置
     */
    @Operation(summary = "修改塔吊阈值配置")
    @PutMapping("/thresholds")
    public Result<Map<String, Boolean>> updateThresholds(
            @Valid @RequestBody TowerCraneThresholdUpdateRequest request) {
        towerCraneService.updateThresholds(request);
        return Result.ok(Map.of("success", true));
    }

    /**
     * 查询塔吊作业记录列表
     */
    @Operation(summary = "查询塔吊作业记录列表")
    @GetMapping("/work-records")
    public Result<PageListVO<TowerCraneWorkRecordVO>> workRecords(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数必须大于0")
            @Max(value = 100, message = "每页条数不能超过100") long pageSize) {
        return Result.ok(towerCraneService.workRecords(deviceId, locationId, startTime, endTime, pageNum, pageSize));
    }

    /**
     * 获取塔吊作业统计汇总
     */
    @Operation(summary = "获取塔吊作业统计汇总")
    @GetMapping("/work-records/statistics")
    public Result<TowerCraneWorkStatisticsVO> workStatistics(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String statDate) {
        return Result.ok(towerCraneService.workStatistics(deviceId, statDate));
    }

    @ExceptionHandler(TowerCraneException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTowerCraneException(TowerCraneException exception) {
        return Result.error(400, exception.getMessage());
    }
}