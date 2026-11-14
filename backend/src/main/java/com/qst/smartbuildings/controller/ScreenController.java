package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.dto.ScreenConfigSaveRequest;
import com.qst.smartbuildings.dto.ScreenVideoRotationSaveRequest;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.service.ScreenService;
import com.qst.smartbuildings.vo.ScreenConfigListData;
import com.qst.smartbuildings.vo.ScreenConfigVO;
import com.qst.smartbuildings.vo.IdResult;
import com.qst.smartbuildings.vo.OperationResult;
import com.qst.smartbuildings.vo.ScreenRealtimeData;
import com.qst.smartbuildings.vo.ScreenVideoRotationListData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "11.数据大屏", description = "数据大屏配置、实时数据和视频轮播")
@RestController
@RequestMapping("/screen")
@Validated
public class ScreenController {
    private final ScreenService screenService;

    public ScreenController(ScreenService screenService) {
        this.screenService = screenService;
    }

    @Operation(summary = "查询数据大屏配置列表")
    @GetMapping("/config/list")
    public Result<ScreenConfigListData> list(
            @Parameter(description = "项目ID", required = true)
            @RequestParam Long projectId,
            @Parameter(description = "状态：1启用，0停用")
            @RequestParam(required = false) Integer status) {
        return Result.ok(screenService.listConfigs(projectId, status));
    }

    @Operation(summary = "新增数据大屏配置")
    @PostMapping("/config/create")
    public Result<IdResult> create(@Valid @RequestBody ScreenConfigSaveRequest request) {
        return Result.ok(new IdResult(screenService.createConfig(request)));
    }

    @Operation(summary = "查询数据大屏配置详情")
    @GetMapping("/config/detail/{screenId}")
    public Result<ScreenConfigVO> detail(
            @Parameter(description = "数据大屏ID", required = true)
            @PathVariable Long screenId) {
        return Result.ok(screenService.getConfig(screenId));
    }

    @Operation(summary = "修改数据大屏配置")
    @PutMapping("/config/update/{screenId}")
    public Result<OperationResult> update(
            @Parameter(description = "数据大屏ID", required = true)
            @PathVariable Long screenId,
            @Valid @RequestBody ScreenConfigSaveRequest request) {
        return Result.ok(new OperationResult(screenService.updateConfig(screenId, request)));
    }

    @Operation(summary = "删除数据大屏配置")
    @DeleteMapping("/config/delete/{screenId}")
    public Result<OperationResult> delete(
            @Parameter(description = "数据大屏ID", required = true)
            @PathVariable Long screenId) {
        return Result.ok(new OperationResult(screenService.deleteConfig(screenId)));
    }

    @Operation(summary = "设置项目默认数据大屏")
    @PutMapping("/config/set-default/{screenId}")
    public Result<OperationResult> setDefault(
            @Parameter(description = "数据大屏ID", required = true)
            @PathVariable Long screenId) {
        return Result.ok(new OperationResult(screenService.setDefaultScreen(screenId)));
    }

    @Operation(summary = "查询综合安全态势大屏实时数据", description = "返回设备状态总览、最新10条未处置告警、塔吊/升降机运行状态、环境指标、近7天告警趋势和视频轮播数据。")
    @GetMapping({"/realtime/get-data/{screenId}", "/realtime/data/{screenId}"})
    public Result<ScreenRealtimeData> realtime(
            @Parameter(description = "数据大屏ID", required = true)
            @PathVariable Long screenId) {
        return Result.ok(screenService.getRealtimeData(screenId));
    }

    @Operation(summary = "查询大屏视频轮播配置")
    @GetMapping("/video-rotation/list/{screenId}")
    public Result<ScreenVideoRotationListData> listVideoRotations(
            @Parameter(description = "数据大屏ID", required = true)
            @PathVariable Long screenId) {
        return Result.ok(screenService.listVideoRotations(screenId));
    }

    @Operation(summary = "批量保存大屏视频轮播配置")
    @PutMapping("/video-rotation/save/{screenId}")
    public Result<OperationResult> saveVideoRotations(
            @Parameter(description = "数据大屏ID", required = true)
            @PathVariable Long screenId,
            @Valid @RequestBody List<ScreenVideoRotationSaveRequest> request) {
        return Result.ok(new OperationResult(screenService.saveVideoRotations(screenId, request)));
    }
}
