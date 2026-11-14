package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.dto.EnvDataBatchReportRequest;
import com.qst.smartbuildings.dto.EnvDataManualRequest;
import com.qst.smartbuildings.dto.EnvDataReportRequest;
import com.qst.smartbuildings.dto.EnvThresholdUpdateRequest;
import com.qst.smartbuildings.dto.EnvMonitorPointRequest;
import com.qst.smartbuildings.exception.EnvMonitorPointNotFoundException;
import com.qst.smartbuildings.service.EnvMonitorPointService;
import com.qst.smartbuildings.vo.EnvMonitorPointVO;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.exception.MonitoringException;
import com.qst.smartbuildings.service.EnvMonitorService;
import com.qst.smartbuildings.vo.EnvBatchPushResult;
import com.qst.smartbuildings.vo.EnvDailyStatVO;
import com.qst.smartbuildings.vo.EnvMonitorRecordVO;
import com.qst.smartbuildings.vo.EnvThresholdVO;
import com.qst.smartbuildings.vo.EnvTrendVO;
import com.qst.smartbuildings.vo.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/env-monitor")
@Validated
@Tag(name = "9.环境监测", description = "环境监测点、实时数据、历史趋势、日报和阈值管理")
public class EnvMonitorController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final EnvMonitorService service;
    private final EnvMonitorPointService pointService;

    public EnvMonitorController(EnvMonitorService service, EnvMonitorPointService pointService) {
        this.service = service;
        this.pointService = pointService;
    }

@Operation(summary = "分页查询环境监测点")
    @GetMapping("/points/page")
    public Result<PageResult<EnvMonitorPointVO>> pointPage(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String location) {
        return Result.ok(pointService.page(pageNum, pageSize, name, location));
    }

    @Operation(summary = "新增环境监测点")
    @PostMapping("/points")
    public Result<Map<String, Long>> createPoint(@Valid @RequestBody EnvMonitorPointRequest request) {
        return Result.ok(Map.of("id", pointService.create(request)));
    }

    @Operation(summary = "查询环境监测点详情")
    @GetMapping("/points/{id}")
    public Result<EnvMonitorPointVO> pointDetail(@PathVariable Long id) {
        return Result.ok(pointService.detail(id));
    }

    @Operation(summary = "修改环境监测点")
    @PutMapping("/points/{id}")
    public Result<Void> updatePoint(@PathVariable Long id, @Valid @RequestBody EnvMonitorPointRequest request) {
        pointService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除环境监测点", description = "存在运行中传感器时拒绝删除，历史数据保留")
    @DeleteMapping("/points/{id}")
    public Result<Void> deletePoint(@PathVariable Long id) {
        pointService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "查询最新环境监测数据")    @GetMapping("/latest")
    public Result<Map<String, List<EnvMonitorRecordVO>>> latest(@RequestParam(required = false) Long monitorPointId,
                                                                @RequestParam(required = false) String metricType) {
        return Result.ok(Map.of("list", service.latest(monitorPointId, metricType)));
    }

    @Operation(summary = "查询环境监测历史数据")
    @GetMapping("/history")
    public Result<PageResult<EnvMonitorRecordVO>> history(@RequestParam Long monitorPointId,
                                                          @RequestParam String metricType,
                                                          @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss，可带 .SSS",
                                                                     example = "2026-08-05 00:00:00")
                                                          @RequestParam String startTime,
                                                          @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss，可带 .SSS",
                                                                     example = "2026-08-05 23:59:59")
                                                          @RequestParam String endTime,
                                                          @RequestParam(defaultValue = "1") @Min(1) long pageNum,
                                                          @RequestParam(defaultValue = "100") @Min(1) @Max(500) long pageSize) {
        return Result.ok(service.history(monitorPointId, metricType, parseTime(startTime), parseTime(endTime), pageNum, pageSize));
    }

    @Operation(summary = "查询环境告警列表")
    @GetMapping("/alarms")
    public Result<PageResult<EnvMonitorRecordVO>> alarms(@RequestParam(required = false) Integer status,
                                                         @RequestParam(required = false) Long monitorPointId,
                                                         @RequestParam(required = false) String metricType,
                                                         @RequestParam(required = false) String startTime,
                                                         @RequestParam(required = false) String endTime,
                                                         @RequestParam(defaultValue = "1") @Min(1) long pageNum,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(500) long pageSize) {
        return Result.ok(service.alarms(status, monitorPointId, metricType, optionalTime(startTime), optionalTime(endTime), pageNum, pageSize));
    }

    @Operation(summary = "查询环境日统计报表")
    @GetMapping("/daily-stat")
    public Result<PageResult<EnvDailyStatVO>> dailyStat(@RequestParam(required = false) Long monitorPointId,
                                                         @RequestParam(required = false) String startDate,
                                                         @RequestParam(required = false) String endDate,
                                                         @RequestParam(required = false) String metricType,
                                                         @RequestParam(defaultValue = "1") @Min(1) long pageNum,
                                                         @RequestParam(defaultValue = "30") @Min(1) @Max(200) long pageSize) {
        return Result.ok(service.dailyStats(monitorPointId, optionalDate(startDate), optionalDate(endDate), metricType, pageNum, pageSize));
    }

    @Operation(summary = "获取环境趋势图数据")
    @GetMapping("/daily-stat/trend")
    public Result<EnvTrendVO> trend(@RequestParam Long monitorPointId,
                                    @RequestParam String startDate,
                                    @RequestParam String endDate,
                                    @RequestParam String metricType) {
        return Result.ok(service.trend(monitorPointId, parseDate(startDate), parseDate(endDate), metricType));
    }

    @Operation(summary = "上报单条环境监测数据")
    @PostMapping("/data")
    public Result<com.qst.smartbuildings.vo.EnvDataPushResult> report(@Valid @RequestBody EnvDataReportRequest request) {
        return Result.ok(service.report(request));
    }

    @Operation(summary = "批量上报环境监测数据")
    @PostMapping("/data/batch")
    public Result<EnvBatchPushResult> reportBatch(@Valid @RequestBody EnvDataBatchReportRequest request) {
        return Result.ok(service.reportBatch(request));
    }

    @Operation(summary = "手动补录环境监测数据")
    @PostMapping("/data/manual")
    public Result<Map<String, Long>> reportManual(@Valid @RequestBody EnvDataManualRequest request) {
        return Result.ok(Map.of("id", service.reportManual(request)));
    }

    @Operation(summary = "查询监测点下传感器阈值")
    @GetMapping("/thresholds")
    public Result<PageResult<EnvThresholdVO>> thresholds(
            @RequestParam(required = false) Long monitorPointId,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        List<EnvThresholdVO> all = service.thresholds(monitorPointId);
        int from = (int) Math.min((pageNum - 1) * pageSize, all.size());
        int to = (int) Math.min(from + pageSize, all.size());
        long pages = all.isEmpty() ? 0 : (all.size() + pageSize - 1) / pageSize;
        return Result.ok(new PageResult<>(all.size(), pages, pageNum, pageSize, all.subList(from, to)));
    }

    @Operation(summary = "修改传感器阈值配置")
    @PutMapping("/thresholds")
    public Result<Map<String, Boolean>> updateThreshold(@Valid @RequestBody EnvThresholdUpdateRequest request) {
        service.updateThreshold(request);
        return Result.ok(Map.of("success", true));
    }

    @ExceptionHandler(EnvMonitorPointNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handlePointNotFound(EnvMonitorPointNotFoundException exception) {
        return Result.error(404, exception.getMessage());
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) throw new MonitoringException("时间不能为空");
        try {
            return LocalDateTime.parse(value, DATE_TIME_MILLIS);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, DATE_TIME);
            } catch (DateTimeParseException ex) {
                throw new MonitoringException("时间格式应为 yyyy-MM-dd HH:mm:ss[.SSS]");
            }
        }
    }

    private static LocalDateTime optionalTime(String value) { return value == null || value.isBlank() ? null : parseTime(value); }
    private static LocalDate optionalDate(String value) { return value == null || value.isBlank() ? null : parseDate(value); }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) throw new MonitoringException("日期不能为空");
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate();
            } catch (DateTimeParseException ignoredIsoDateTime) {
                try {
                    return parseTime(value).toLocalDate();
                } catch (MonitoringException ignoredSpaceDateTime) {
                    throw new MonitoringException("日期格式应为 yyyy-MM-dd 或 yyyy-MM-dd[T ]HH:mm:ss[.SSS]");
                }
            }
        }
    }
}
