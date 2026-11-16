package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.dto.CameraCreateRequest;
import com.qst.smartbuildings.dto.CameraUpdateRequest;
import com.qst.smartbuildings.dto.DeleteAiSnapshotsRequest;
import com.qst.smartbuildings.dto.DeleteByConditionRequest;
import com.qst.smartbuildings.dto.DeleteTimelineRequest;
import com.qst.smartbuildings.dto.DeleteVideoRecordsRequest;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.exception.CameraException;
import com.qst.smartbuildings.service.VideoMonitoringService;
import com.qst.smartbuildings.vo.DeleteResponseData;
import com.qst.smartbuildings.vo.DeleteTimelineResponseData;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.VideoCameraVO;
import com.qst.smartbuildings.vo.VideoRecordTimelineVO;
import com.qst.smartbuildings.vo.VideoRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

@Tag(name = "视频监控", description = "视频录像/截图、摄像头管理、时间线和实时推送")
@RestController
@RequestMapping("/video")
@Validated
public class VideoMonitoringController {

    private final VideoMonitoringService videoMonitoringService;

    public VideoMonitoringController(VideoMonitoringService videoMonitoringService) {
        this.videoMonitoringService = videoMonitoringService;
    }

    @Operation(summary = "查询录像/截图列表")
    @GetMapping("/video-records")
    public Result<PageResult<VideoRecordVO>> pageVideoRecords(
            @RequestParam Long cameraId,
            @RequestParam(required = false) Integer recordType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数必须大于0") @Max(value = 100, message = "每页条数不能超过100") long pageSize) {
        return Result.ok(videoMonitoringService.pageVideoRecords(cameraId, recordType, startTime, endTime, pageNum, pageSize));
    }

    @Operation(summary = "获取AI告警截图列表")
    @GetMapping("/video-records/ai-snapshots")
    public Result<PageResult<VideoRecordVO>> pageAiSnapshots(
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数必须大于0") @Max(value = 100, message = "每页条数不能超过100") long pageSize) {
        return Result.ok(videoMonitoringService.pageAiSnapshots(cameraId, startTime, endTime, pageNum, pageSize));
    }

    @Operation(summary = "按时间线查询录像（时间线播放器）")
    @GetMapping({"/video-records/timeline", "/video-records/segments"})
    public Result<VideoRecordTimelineVO> timeline(@RequestParam Long cameraId, @RequestParam String date) {
        return Result.ok(videoMonitoringService.getTimeline(cameraId, date));
    }

    @Operation(summary = "批量删除录像/截图记录", description = "按记录ID列表批量删除录像或截图记录，最多200条，可选删除物理文件")
    @DeleteMapping("/video-records")
    public Result<DeleteResponseData> deleteVideoRecords(@Valid @RequestBody DeleteVideoRecordsRequest request) {
        return Result.ok(videoMonitoringService.deleteVideoRecords(request));
    }

    @Operation(summary = "删除单条录像/截图记录", description = "软删除策略：标记已删除，可选清理物理文件")
    @DeleteMapping("/video-records/{id}")
    public Result<VideoRecordVO> deleteVideoRecordById(@PathVariable @Min(1) Long id,
                                                       @RequestParam(defaultValue = "true") boolean deleteFile) {
        return Result.ok(videoMonitoringService.deleteVideoRecordById(id, deleteFile));
    }

    @Operation(summary = "批量删除AI告警截图", description = "支持按ID列表或按摄像头+时间范围+AI标签条件删除，最多200条")
    @DeleteMapping("/video-records/ai-snapshots")
    public Result<DeleteResponseData> deleteAiSnapshots(@Valid @RequestBody DeleteAiSnapshotsRequest request) {
        return Result.ok(videoMonitoringService.deleteAiSnapshots(request));
    }

    @Operation(summary = "删除单条AI告警截图", description = "同时清除关联的AI识别标签和置信度数据")
    @DeleteMapping("/video-records/ai-snapshots/{id}")
    public Result<VideoRecordVO> deleteAiSnapshotById(@PathVariable @Min(1) Long id,
                                                      @RequestParam(defaultValue = "true") boolean deleteFile) {
        return Result.ok(videoMonitoringService.deleteAiSnapshotById(id, deleteFile));
    }

    @Operation(summary = "删除指定摄像头某天全部录像片段", description = "[注意] 高危操作，需 confirm=true 二次确认，删除后不可恢复")
    @DeleteMapping("/video-records/timeline")
    public Result<DeleteTimelineResponseData> deleteTimeline(@Valid @RequestBody DeleteTimelineRequest request) {
        return Result.ok(videoMonitoringService.deleteTimeline(request));
    }

    @Operation(summary = "按条件批量删除录像/截图", description = "按摄像头+记录类型+时间范围批量删除，时间范围不超过31天，单次最多1000条")
    @DeleteMapping("/video-records/batch")
    public Result<DeleteResponseData> deleteByCondition(@Valid @RequestBody DeleteByConditionRequest request) {
        return Result.ok(videoMonitoringService.deleteByCondition(request));
    }

    @Operation(summary = "查询摄像头列表")
    @GetMapping("/cameras")
    public Result<PageResult<VideoCameraVO>> pageCameras(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Integer onlineStatus,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String cameraName,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数必须大于0") @Max(value = 100, message = "每页条数不能超过100") long pageSize) {
        return Result.ok(videoMonitoringService.pageCameras(locationId, onlineStatus, deviceId, cameraName, pageNum, pageSize));
    }

    @Operation(summary = "新增摄像头")
    @PostMapping("/cameras")
    public Result<java.util.Map<String, Long>> createCamera(@Valid @RequestBody CameraCreateRequest request) {
        return Result.ok(java.util.Map.of("id", videoMonitoringService.createCamera(request)));
    }

    @Operation(summary = "更新摄像头信息")
    @PutMapping("/cameras/{id}")
    public Result<java.util.Map<String, Boolean>> updateCamera(@PathVariable Long id, @Valid @RequestBody CameraUpdateRequest request) {
        return Result.ok(java.util.Map.of("success", videoMonitoringService.updateCamera(id, request)));
    }

    @Operation(summary = "删除摄像头（逻辑删除）")
    @DeleteMapping("/cameras/{id}")
    public Result<java.util.Map<String, Boolean>> deleteCamera(@PathVariable Long id) {
        return Result.ok(java.util.Map.of("success", videoMonitoringService.deleteCamera(id)));
    }

    @Operation(summary = "获取摄像头视频流地址")
    @GetMapping("/cameras/{id}/stream-url")
    public Result<VideoCameraVO> getStreamUrl(@PathVariable Long id) {
        return Result.ok(videoMonitoringService.getStreamUrl(id));
    }

    @ExceptionHandler(CameraException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleCameraException(CameraException exception) {
        return Result.error(exception.getCode(), exception.getMsg() == null ? "视频监控请求失败" : exception.getMsg());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return Result.error(SystemCodeEnum.BAD_REQUEST.getCode(), "参数格式不正确：" + exception.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException exception) {
        return Result.error(SystemCodeEnum.BAD_REQUEST.getCode(), "缺少请求参数：" + exception.getParameterName());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception exception) {
        return Result.error(SystemCodeEnum.SYSTEM_ERROR.getCode(), "系统内部错误");
    }
}
