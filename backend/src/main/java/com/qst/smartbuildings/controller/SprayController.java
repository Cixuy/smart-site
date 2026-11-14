package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.dto.*;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.service.SprayService;
import com.qst.smartbuildings.vo.SprayActiveTaskVO;
import com.qst.smartbuildings.vo.SprayOperateResultVO;
import com.qst.smartbuildings.vo.SprayOperationLogVO;
import com.qst.smartbuildings.vo.SprayScheduleVO;
import com.qst.smartbuildings.vo.SprayStatisticsVO;
import com.qst.smartbuildings.vo.SprayStatusVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Arrays;
import java.util.List;

/**
 * 喷淋降尘系统控制器
 * <p>完整路径前缀为 /api/spray（application.yml 配置了 context-path=/api）。</p>
 */
@RestController
@RequestMapping("/spray")
@Validated
public class SprayController {

    private final SprayService sprayService;

    public SprayController(SprayService sprayService) {
        this.sprayService = sprayService;
    }

    // ======================== 手动启停 ========================

    @PostMapping("/operate/start")
    public Result<SprayOperateResultVO> start(@Valid @RequestBody SprayOperateRequest req) {
        return Result.ok(sprayService.start(req));
    }

    @PostMapping("/operate/stop")
    public Result<SprayOperateResultVO> stop(@Valid @RequestBody SprayOperateRequest req) {
        return Result.ok(sprayService.stop(req));
    }

    @GetMapping("/operate/status")
    public Result<SprayStatusVO> status(@RequestParam Long locationId) {
        return Result.ok(sprayService.status(locationId));
    }

    @GetMapping("/operate/status/batch")
    public Result<List<SprayStatusVO>> statusBatch(@RequestParam(required = false) Long parentLocationId) {
        return Result.ok(sprayService.statusBatch(parentLocationId));
    }

    // ======================== 自动联动（内部接口） ========================

    @PostMapping("/operate/trigger")
    public Result<SprayOperateResultVO> trigger(@Valid @RequestBody SprayTriggerRequest req) {
        return Result.ok(sprayService.trigger(req));
    }

    // ======================== 操作审计 ========================

    @GetMapping("/operationLog/page")
    public Result<IPage<SprayOperationLogVO>> logPage(SprayOperationLogPageRequest req) {
        return Result.ok(sprayService.logPage(req));
    }

    @GetMapping("/operationLog/{id}")
    public Result<SprayOperationLogVO> logDetail(@PathVariable Long id) {
        return Result.ok(sprayService.logDetail(id));
    }

    @GetMapping("/operationLog/export")
    public void exportLog(SprayOperationLogPageRequest req, HttpServletResponse response) {
        sprayService.exportLog(req, response);
    }

    @GetMapping("/statistics")
    public Result<SprayStatisticsVO> statistics(SprayStatisticsRequest req) {
        return Result.ok(sprayService.statistics(req));
    }

    // ======================== 定时任务 ========================

    @PostMapping("/schedule")
    public Result<SprayScheduleVO> createSchedule(@Valid @RequestBody SprayScheduleRequest req) {
        return Result.ok(sprayService.createSchedule(req));
    }

    @GetMapping("/schedule/page")
    public Result<IPage<SprayScheduleVO>> schedulePage(SpraySchedulePageRequest req) {
        return Result.ok(sprayService.schedulePage(req));
    }

    @GetMapping("/schedule/{id}")
    public Result<SprayScheduleVO> scheduleDetail(@PathVariable Long id) {
        return Result.ok(sprayService.scheduleDetail(id));
    }

    @PutMapping("/schedule")
    public Result<SprayScheduleVO> updateSchedule(@Valid @RequestBody SprayScheduleRequest req) {
        return Result.ok(sprayService.updateSchedule(req));
    }

    @DeleteMapping("/schedule/{ids}")
    public Result<Void> deleteSchedule(@PathVariable String ids) {
        sprayService.deleteSchedule(Arrays.stream(ids.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList());
        return Result.ok();
    }

    @PutMapping("/schedule/{id}/status")
    public Result<Void> scheduleStatus(@PathVariable Long id, @Valid @RequestBody SprayScheduleStatusRequest req) {
        sprayService.scheduleStatus(id, req.getStatus());
        return Result.ok();
    }

    @GetMapping("/schedule/conflictCheck")
    public Result<Boolean> conflictCheck(SprayConflictCheckRequest req) {
        return Result.ok(sprayService.conflictCheck(req));
    }

    @GetMapping("/schedule/active")
    public Result<List<SprayActiveTaskVO>> activeTasks() {
        return Result.ok(sprayService.activeTasks());
    }
}
