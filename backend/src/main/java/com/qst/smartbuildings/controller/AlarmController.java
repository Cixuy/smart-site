package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.common.AuthConst;
import com.qst.smartbuildings.dto.AiScreenshotCreateDTO;
import com.qst.smartbuildings.dto.AlarmCreateDTO;
import com.qst.smartbuildings.dto.AlarmDisposalDTO;
import com.qst.smartbuildings.dto.AlarmRecoveryDTO;
import com.qst.smartbuildings.dto.UserVO;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.exception.AlarmException;
import com.qst.smartbuildings.exception.AlarmNotFoundException;
import com.qst.smartbuildings.service.AlarmService;
import com.qst.smartbuildings.service.UserService;
import com.qst.smartbuildings.vo.AlarmDetailVO;
import com.qst.smartbuildings.vo.AlarmDistributionVO;
import com.qst.smartbuildings.vo.AlarmHistoryVO;
import com.qst.smartbuildings.vo.AlarmListVO;
import com.qst.smartbuildings.vo.AlarmOverviewVO;
import com.qst.smartbuildings.vo.AlarmPushLogVO;
import com.qst.smartbuildings.vo.AlarmTimelinessVO;
import com.qst.smartbuildings.vo.AlarmTrendVO;
import com.qst.smartbuildings.vo.AiAlarmDetailVO;
import com.qst.smartbuildings.vo.DailyAlarmStatVO;
import com.qst.smartbuildings.vo.DisposalRecordVO;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.RecoveryRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "告警管理", description = "告警查询、处置、恢复、统计和推送日志接口")
@RestController
@Validated
public class AlarmController {
    private final AlarmService alarmService;
    private final UserService userService;

    public AlarmController(AlarmService alarmService, UserService userService) {
        this.alarmService = alarmService;
        this.userService = userService;
    }

    @Operation(summary = "告警处置", description = "提交告警处置记录并将告警状态更新为已处置")
    @PostMapping("/alarms/disposal")
    public Result<Void> dispose(@Valid @RequestBody AlarmDisposalDTO request,
                                HttpServletRequest httpRequest,
                                @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
                                @RequestHeader(value = "X-User-Name", required = false) String operatorName) {
        Operator operator = resolveOperator(httpRequest, operatorId, operatorName);
        alarmService.dispose(request, operator.id(), operator.name());
        return Result.ok();
    }

    @Operation(summary = "生成统一告警", description = "供设备、环境和 AI 模块写入统一告警记录")
    @PostMapping("/alarms")
    public Result<Map<String, Long>> create(@Valid @RequestBody AlarmCreateDTO request) {
        return Result.ok(Map.of("id", alarmService.create(request)));
    }

    @Operation(summary = "查询告警详情", description = "返回告警、处置、恢复、关联告警、截图和最新推送状态")
    @GetMapping("/alarms/{id}")
    public Result<AlarmDetailVO> detail(@PathVariable @Min(1) Long id) {
        return Result.ok(alarmService.detail(id));
    }

    @Operation(summary = "逻辑删除告警", description = "通过 is_deleted 标记删除告警")
    @DeleteMapping("/alarms/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        alarmService.logicalDelete(id);
        return Result.ok();
    }

    @Operation(summary = "接单处理告警", description = "将未处置告警流转为处置中状态")
    @PostMapping("/alarms/{id}/claim")
    public Result<Void> claim(@PathVariable @Min(1) Long id) {
        alarmService.claim(id);
        return Result.ok();
    }

    @Operation(summary = "分页查询告警处置记录", description = "按处置时间倒序返回指定告警的处置历史")
    @GetMapping("/alarms/{id}/disposals")
    public Result<PageResult<DisposalRecordVO>> disposals(@PathVariable @Min(1) Long id,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return Result.ok(alarmService.disposals(id, pageNum, pageSize));
    }

    @Operation(summary = "写入告警自动恢复记录", description = "重复请求幂等，恢复时间支持毫秒精度")
    @PostMapping("/alarms/recovery")
    public Result<Void> recover(@Valid @RequestBody AlarmRecoveryDTO request) {
        alarmService.recover(request);
        return Result.ok();
    }

    @Operation(summary = "查询告警恢复记录")
    @GetMapping("/alarms/{id}/recovery")
    public Result<RecoveryRecordVO> recovery(@PathVariable @Min(1) Long id) {
        return Result.ok(alarmService.recovery(id));
    }

    @Operation(summary = "查询关联告警组", description = "通过根告警 ID 返回同一事件的全部告警")
    @GetMapping("/alarms/{id}/related")
    public Result<List<AlarmListVO>> related(@PathVariable @Min(1) Long id) {
        return Result.ok(alarmService.related(id));
    }

    @Operation(summary = "分页查询统一告警列表", description = "支持项目、来源、设备类型、级别、状态、关联对象、关键词和时间筛选")
    @GetMapping("/alarms")
    public Result<PageResult<AlarmListVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer sourceType,
            @RequestParam(required = false) Long deviceTypeId,
            @RequestParam(required = false) Integer alarmLevel,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String alarmTypeCode,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long monitorPointId,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) Long relatedAlarmId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.ok(alarmService.page(pageNum, pageSize, projectId, sourceType, deviceTypeId, alarmLevel,
                status, alarmTypeCode, deviceId, monitorPointId, cameraId, relatedAlarmId, keyword, startTime, endTime));
    }

    @Operation(summary = "查询告警记录列表", description = "兼容原告警记录接口，支持精确筛选与白名单排序")
    @GetMapping("/alarms/record")
    public Result<PageResult<AlarmListVO>> recordPage(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long pageSize,
            @RequestParam(required = false) Long projectId, @RequestParam(required = false) Integer sourceType,
            @RequestParam(required = false) Integer alarmLevel, @RequestParam(required = false) String alarmTypeCode,
            @RequestParam(required = false) Integer status, @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long cameraId, @RequestParam(required = false) Long monitorPointId,
            @RequestParam(required = false) String alarmNo, @RequestParam(required = false) String alarmContent,
            @RequestParam(required = false) Long relatedAlarmId,
            @RequestParam(defaultValue = "0") Integer isDeleted,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss[.SSS]") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss[.SSS]") LocalDateTime endTime,
            @RequestParam(defaultValue = "alarm_time") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.ok(alarmService.recordPage(pageNum, pageSize, projectId, sourceType, alarmLevel,
                alarmTypeCode, status, deviceId, cameraId, monitorPointId, alarmNo, alarmContent, relatedAlarmId,
                isDeleted, startTime, endTime, sortField, sortOrder));
    }

    @Operation(summary = "查询告警总览", description = "按处置状态、告警级别和告警来源返回数量及占比")
    @GetMapping("/alarms/overview")
    public Result<AlarmOverviewVO> overview(@RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.ok(alarmService.overview(projectId, startTime, endTime));
    }

    @Operation(summary = "查询告警趋势", description = "从告警日统计表按日、周或月聚合")
    @GetMapping("/alarms/statistics/trend")
    public Result<AlarmTrendVO> trend(@RequestParam Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAY") String granularity,
            @RequestParam(required = false) Integer sourceType,
            @RequestParam(required = false) Integer alarmLevel) {
        return Result.ok(alarmService.trend(projectId, startDate, endDate, granularity, sourceType, alarmLevel));
    }

    @Operation(summary = "查询告警分布", description = "按告警类型或设备类型统计数量及占比")
    @GetMapping("/alarms/statistics/distribution")
    public Result<AlarmDistributionVO> distribution(@RequestParam Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "ALARM_TYPE") String dimension,
            @RequestParam(required = false) Integer sourceType) {
        return Result.ok(alarmService.distribution(projectId, startTime, endTime, dimension, sourceType));
    }

    @Operation(summary = "查询告警处置及时率", description = "以每条告警的首次处置时间计算")
    @GetMapping("/alarms/statistics/timeliness")
    public Result<AlarmTimelinessVO> timeliness(@RequestParam Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "30") @Min(1) @Max(10080) Integer deadlineMinutes,
            @RequestParam(required = false) Long operatorId) {
        return Result.ok(alarmService.timeliness(projectId, startTime, endTime, deadlineMinutes, operatorId));
    }

    @Operation(summary = "导出告警统计报表", description = "支持 CSV 和 XLSX")
    @GetMapping("/alarms/statistics/export")
    public ResponseEntity<byte[]> export(@RequestParam Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "XLSX") String format,
            @RequestParam(required = false) Integer sourceType,
            @RequestParam(required = false) Integer alarmLevel,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(10080) Integer deadlineMinutes) {
        String normalized = format.toUpperCase();
        byte[] body = alarmService.export(projectId, startTime, endTime, normalized, sourceType, alarmLevel,
                status, operatorId);
        String extension = "CSV".equals(normalized) ? "csv" : "xlsx";
        MediaType mediaType = "CSV".equals(normalized)
                ? MediaType.parseMediaType("text/csv;charset=UTF-8")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = URLEncoder.encode("告警统计." + extension, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(mediaType).body(body);
    }

    @Operation(summary = "分页查询告警推送记录")
    @GetMapping("/alarms/{id}/push-logs")
    public Result<PageResult<AlarmPushLogVO>> pushLogs(@PathVariable @Min(1) Long id,
            @RequestParam(required = false) Integer pushStatus,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return Result.ok(alarmService.pushLogs(id, pushStatus, pageNum, pageSize));
    }

    @Operation(summary = "重试失败的告警推送", description = "将失败记录重置为待推送并累加重试次数")
    @PostMapping("/alarms/push-logs/{pushLogId}/retry")
    public Result<Void> retryPush(@PathVariable @Min(1) Long pushLogId) {
        alarmService.retryPush(pushLogId);
        return Result.ok();
    }

    @Operation(summary = "获取告警闭环历史", description = "查询告警的全部处置记录和恢复记录")
    @GetMapping("/alarms/{id}/history")
    public Result<AlarmHistoryVO> history(@PathVariable @Min(1) Long id) {
        return Result.ok(alarmService.history(id));
    }

    @Operation(summary = "查询告警日统计", description = "获取指定项目指定日期的告警级别和来源统计")
    @GetMapping("/alarms/statistics/daily")
    public Result<List<DailyAlarmStatVO>> dailyStatistics(@RequestParam Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(alarmService.dailyStatistics(projectId, date));
    }

    @Operation(summary = "分页查询 AI 告警", description = "按时间、处置状态和告警类型筛选 AI 识别告警")
    @GetMapping("/ai/alarms")
    public Result<PageResult<AlarmListVO>> pageAi(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) long pageSize,
            @Parameter(description = "告警开始时间，ISO-8601，支持毫秒")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "告警结束时间，ISO-8601，支持毫秒")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String alarmTypeCode) {
        return Result.ok(alarmService.pageAi(pageNum, pageSize, startTime, endTime, status, alarmTypeCode));
    }

    @Operation(summary = "获取 AI 告警详情", description = "查询单条 AI 告警并返回关联截图地址")
    @GetMapping("/ai/alarms/{id}")
    public Result<AiAlarmDetailVO> aiDetail(@PathVariable @Min(1) Long id) {
        return Result.ok(alarmService.aiDetail(id));
    }

    @Operation(summary = "关联 AI 告警截图", description = "为指定 AI 告警写入截图地址，重复请求幂等")
    @PostMapping("/ai/alarms/{id}/screenshot")
    public Result<Map<String, Long>> addScreenshot(@PathVariable @Min(1) Long id,
                                                   @Valid @RequestBody AiScreenshotCreateDTO request) {
        return Result.ok(Map.of("id", alarmService.addScreenshot(id, request)));
    }

    @Operation(summary = "删除 AI 告警的全部截图", description = "删除指定 AI 告警关联的全部截图记录及本地图片文件，返回删除条数")
    @DeleteMapping("/ai/alarms/{id}/screenshots")
    public Result<Map<String, Integer>> deleteAiScreenshots(@PathVariable @Min(1) Long id) {
        return Result.ok(Map.of("count", alarmService.deleteAiScreenshots(id)));
    }

    @Operation(summary = "删除单条 AI 截图", description = "按截图主键删除数据库记录及本地图片文件")
    @DeleteMapping("/ai/screenshots/{id}")
    public Result<Void> deleteAiScreenshot(@PathVariable @Min(1) Long id) {
        alarmService.deleteAiScreenshot(id);
        return Result.ok();
    }

    private Operator resolveOperator(HttpServletRequest request, Long headerId, String headerName) {
        if (headerId != null) {
            return new Operator(headerId, StringUtils.hasText(headerName) ? headerName : "系统用户");
        }
        String token = request == null ? null : request.getHeader(AuthConst.TOKEN_NAME);
        if (StringUtils.hasText(token)) {
            if (token.startsWith(AuthConst.BEARER_PREFIX)) token = token.substring(AuthConst.BEARER_PREFIX.length());
            try {
                UserVO user = userService.getUserInfo(token);
                if (user != null && user.getId() != null) {
                    return new Operator(user.getId(), StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
                }
            } catch (RuntimeException ignored) {
                // Authentication interceptor provides the authoritative rejection when enabled.
            }
        }
        throw new AlarmException("告警处置必须携带有效登录 Token");
    }

    private record Operator(Long id, String name) { }

    @ExceptionHandler(AlarmNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleAlarmNotFound(AlarmNotFoundException exception) {
        return Result.error(404, exception.getMessage());
    }

    @ExceptionHandler(AlarmException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleAlarmException(AlarmException exception) {
        return Result.error(400, exception.getMessage());
    }
}