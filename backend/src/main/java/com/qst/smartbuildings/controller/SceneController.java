package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.dto.DeviceCoordinateCreateDTO;
import com.qst.smartbuildings.dto.DeviceCoordinateUpdateDTO;
import com.qst.smartbuildings.dto.SceneConfigCreateDTO;
import com.qst.smartbuildings.dto.VideoOverlayCreateDTO;
import com.qst.smartbuildings.dto.VideoOverlayUpdateDTO;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.service.SceneService;
import com.qst.smartbuildings.vo.AlarmDeviceVO;
import com.qst.smartbuildings.vo.DeviceCoordinateVO;
import com.qst.smartbuildings.vo.DeviceDetailVO;
import com.qst.smartbuildings.vo.ModelUploadResultVO;
import com.qst.smartbuildings.vo.SceneConfigVO;
import com.qst.smartbuildings.vo.VideoOverlayVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 3D可视化场景控制器
 * <p>完整路径前缀为 /api/scene（application.yml 配置了 context-path=/api）。</p>
 * <p>认证主体由 AuthInterceptor 写入 UserContext，本类不解析 token。写操作权限在 SceneServiceImpl 内校验。</p>
 */
@RestController
@RequestMapping("/scene")
@Tag(name = "3D可视化场景", description = "3D数字孪生场景配置、设备坐标映射与视频叠加管理（Base Path /api/scene）")
@Validated
public class SceneController {

    private final SceneService sceneService;

    public SceneController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    // ======================== 场景配置 ========================

    @Operation(summary = "按项目查询3D场景配置", description = "加载场景时获取配置，场景不存在返回 404")
    @GetMapping("/config/get-by-project/{projectId}")
    public Result<SceneConfigVO> getByProject(@PathVariable Long projectId) {
        return Result.ok(sceneService.getByProject(projectId));
    }

    @Operation(summary = "新增3D场景配置", description = "创建新场景，一项目一场景；仅系统管理员")
    @PostMapping("/config/create")
    public Result<Long> create(@Valid @RequestBody SceneConfigCreateDTO dto) {
        return Result.ok(sceneService.createConfig(dto));
    }

    @Operation(summary = "查询3D场景配置详情", description = "查看场景详情，场景不存在返回 404")
    @GetMapping("/config/detail/{sceneId}")
    public Result<SceneConfigVO> detail(@PathVariable Long sceneId) {
        return Result.ok(sceneService.getConfigDetail(sceneId));
    }

    @Operation(summary = "修改3D场景配置", description = "更新场景信息（projectId 不可改）；仅系统管理员")
    @PutMapping("/config/update/{sceneId}")
    public Result<Void> update(@PathVariable Long sceneId, @Valid @RequestBody SceneConfigCreateDTO dto) {
        sceneService.updateConfig(sceneId, dto);
        return Result.ok();
    }

    @Operation(summary = "删除3D场景配置", description = "删除场景及关联的坐标映射、视频叠加；仅系统管理员")
    @DeleteMapping("/config/delete/{sceneId}")
    public Result<Void> delete(@PathVariable Long sceneId) {
        sceneService.deleteConfig(sceneId);
        return Result.ok();
    }

    @Operation(summary = "上传设备3D模型文件", description = "上传某设备的3D模型（仅 .glb，glTF 二进制），按设备类型标签与设备编码存入 resources/device-models/{projectId}/{sceneId}/{iconTypeLabel}/{deviceCode}.glb；模型访问地址由路径约定派生，不落库。仅系统管理员。参数：deviceId 必填，iconType 首次上传必填")
    @PostMapping("/model/upload/{sceneId}")
    public Result<ModelUploadResultVO> uploadModel(@PathVariable Long sceneId,
                                                   @RequestParam Long deviceId,
                                                   @RequestParam(required = false) Integer iconType,
                                                   @RequestParam("file") MultipartFile file) {
        return Result.ok(sceneService.uploadModel(sceneId, deviceId, iconType, file));
    }

    // ======================== 设备坐标映射 ========================

    @Operation(summary = "查询3D场景设备坐标与实时状态", description = "加载场景所有设备标注（高频接口），可按图标类型筛选")
    @GetMapping("/device/list/{sceneId}")
    public Result<List<DeviceCoordinateVO>> listDevices(@PathVariable Long sceneId,
                                                        @RequestParam(required = false) Integer iconType) {
        return Result.ok(sceneService.listDevices(sceneId, iconType));
    }

    @Operation(summary = "新增设备3D坐标映射", description = "绑定设备到3D坐标，重复设备返回 409；仅系统管理员")
    @PostMapping("/device-coordinate/create/{sceneId}")
    public Result<Long> createCoordinate(@PathVariable Long sceneId,
                                         @Valid @RequestBody DeviceCoordinateCreateDTO dto) {
        return Result.ok(sceneService.createDeviceCoordinate(sceneId, dto));
    }

    @Operation(summary = "查询3D设备详情卡片", description = "点击设备弹出详情卡片，设备未标注返回 404")
    @GetMapping("/device/detail/{sceneId}/{deviceId}")
    public Result<DeviceDetailVO> deviceDetail(@PathVariable Long sceneId, @PathVariable Long deviceId) {
        return Result.ok(sceneService.getDeviceDetail(sceneId, deviceId));
    }

    @Operation(summary = "修改设备3D坐标映射", description = "调整设备位置/旋转/缩放/图标类型；仅系统管理员")
    @PutMapping("/device-coordinate/update/{coordinateId}")
    public Result<Void> updateCoordinate(@PathVariable Long coordinateId,
                                         @Valid @RequestBody DeviceCoordinateUpdateDTO dto) {
        sceneService.updateDeviceCoordinate(coordinateId, dto);
        return Result.ok();
    }

    @Operation(summary = "删除设备3D坐标映射", description = "移除设备标注及关联视频叠加；仅系统管理员")
    @DeleteMapping("/device-coordinate/delete/{coordinateId}")
    public Result<Void> deleteCoordinate(@PathVariable Long coordinateId) {
        sceneService.deleteDeviceCoordinate(coordinateId);
        return Result.ok();
    }

    @Operation(summary = "查询3D场景告警高亮设备", description = "获取需要高亮闪烁的设备（按最高告警级别）")
    @GetMapping("/alarm-device/list/{sceneId}")
    public Result<List<AlarmDeviceVO>> alarmDevices(@PathVariable Long sceneId) {
        return Result.ok(sceneService.listAlarmDevices(sceneId));
    }

    // ======================== 视频叠加 ========================

    @Operation(summary = "查询3D场景视频叠加配置", description = "获取场景内所有摄像头视频叠加配置")
    @GetMapping("/video-overlay/list/{sceneId}")
    public Result<List<VideoOverlayVO>> listOverlays(@PathVariable Long sceneId) {
        return Result.ok(sceneService.listVideoOverlays(sceneId));
    }

    @Operation(summary = "新增3D视频叠加配置", description = "开启摄像头视频叠加，最多4路，非摄像头坐标返回 400；仅系统管理员")
    @PostMapping("/video-overlay/create")
    public Result<Long> createOverlay(@Valid @RequestBody VideoOverlayCreateDTO dto) {
        return Result.ok(sceneService.createVideoOverlay(dto));
    }

    @Operation(summary = "修改3D视频叠加配置", description = "调整视频窗口参数；仅系统管理员")
    @PutMapping("/video-overlay/update/{overlayId}")
    public Result<Void> updateOverlay(@PathVariable Long overlayId,
                                      @Valid @RequestBody VideoOverlayUpdateDTO dto) {
        sceneService.updateVideoOverlay(overlayId, dto);
        return Result.ok();
    }

    @Operation(summary = "删除3D视频叠加配置", description = "关闭视频叠加；仅系统管理员")
    @DeleteMapping("/video-overlay/delete/{overlayId}")
    public Result<Void> deleteOverlay(@PathVariable Long overlayId) {
        sceneService.deleteVideoOverlay(overlayId);
        return Result.ok();
    }
}
