package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.exception.DeviceStatsException;
import com.qst.smartbuildings.service.DeviceStatsService;
import com.qst.smartbuildings.vo.DeviceOnlineRateVO;
import com.qst.smartbuildings.vo.DeviceOnlineStatsVO;
import com.qst.smartbuildings.vo.OfflineLogStatsVO;
import com.qst.smartbuildings.vo.OfflineLogVO;
import com.qst.smartbuildings.vo.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Tag(name = "设备在线/离线统计", description = "设备在线率统计、单设备在线率、离线记录分页/详情/统计")
@RestController
@RequestMapping("/asset")
@Validated
public class DeviceStatsController {

    @Autowired
    private DeviceStatsService deviceStatsService;

//    @Operation(summary = "设备在线率统计（全局/按位置）", description = "统计指定分区或全局的设备在线率，locationId 不传则全局统计")
//    @GetMapping("/device/onlineStats")
//    public Result<DeviceOnlineStatsVO> onlineStats(@RequestParam(required = false) Long locationId) {
//        return Result.ok(deviceStatsService.getOnlineStats(locationId));
//    }
//
//    @Operation(summary = "单设备在线率查询", description = "查询单台设备在指定时间段内的在线率")
//    @GetMapping("/device/{id}/onlineRate")
//    public Result<DeviceOnlineRateVO> deviceOnlineRate(@PathVariable Long id,
//                                                       @RequestParam String beginTime,
//                                                       @RequestParam String endTime) {
//        return Result.ok(deviceStatsService.getDeviceOnlineRate(id, beginTime, endTime));
//    }

    @Operation(summary = "分页查询离线记录", description = "支持多维度筛选，默认按离线时间倒序")
    @GetMapping("/offlineLog/page")
    public Result<PageResult<OfflineLogVO>> offlineLogPage(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数必须大于0") @Max(value = 100, message = "每页条数不能超过100") long pageSize,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer isOngoing) {
        return Result.ok(deviceStatsService.pageOfflineLogs(pageNum, pageSize, deviceId, reason, beginTime, endTime, isOngoing));
    }

    @Operation(summary = "查询离线记录详情", description = "查询单条离线记录的完整信息")
    @GetMapping("/offlineLog/{id}")
    public Result<OfflineLogVO> offlineLogDetail(@PathVariable Long id) {
        return Result.ok(deviceStatsService.getOfflineLogDetail(id));
    }

    @Operation(summary = "获取设备离线统计", description = "统计指定时间段内的离线次数与总/平均/最长离线时长")
    @GetMapping("/offlineLog/stats")
    public Result<OfflineLogStatsVO> offlineStats(@RequestParam(required = false) Long deviceId,
                                                  @RequestParam String beginTime,
                                                  @RequestParam String endTime) {
        return Result.ok(deviceStatsService.getOfflineStats(deviceId, beginTime, endTime));
    }

    // ==================== 异常处理 ====================

    @ExceptionHandler(DeviceStatsException.class)
    public Result<Void> handleDeviceStatsException(DeviceStatsException e) {
        return Result.error(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(SystemCodeEnum.BAD_REQUEST.getCode(), "参数格式不正确：" + e.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        return Result.error(SystemCodeEnum.BAD_REQUEST.getCode(), "缺少请求参数：" + e.getParameterName());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        return Result.error(SystemCodeEnum.SYSTEM_ERROR.getCode(), "系统内部错误");
    }
}
