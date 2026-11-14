package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 设备3D坐标映射视图（含实时状态）
 * <p>对应设计文档 3.2.1 响应。deviceCode/deviceName/onlineStatus/enableStatus/latestAlarmLevel 依赖设备台账与告警模块，
 * 相关模块尚未实现时以占位值返回（见 SceneServiceImpl 中 TODO）。</p>
 */
@Data
@Schema(description = "设备3D坐标映射视图（含实时状态）")
public class DeviceCoordinateVO {

    @Schema(description = "坐标映射ID")
    private Long coordinateId;

    @Schema(description = "场景ID")
    private Long sceneId;

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "设备编号（设备台账模块提供，暂占位）")
    private String deviceCode;

    @Schema(description = "设备名称（设备台账模块提供，暂占位）")
    private String deviceName;

    @Schema(description = "在线状态：1-在线，0-离线（实时监控提供，暂置1）")
    private Integer onlineStatus;

    @Schema(description = "启用状态：1-启用，0-停用（设备台账提供，暂置1）")
    private Integer enableStatus;

    @Schema(description = "最新告警级别：warning/alarm/control（告警模块提供，暂置null）")
    private String latestAlarmLevel;

    @Schema(description = "三维坐标")
    private Coordinate3DVO position;

    @Schema(description = "旋转角")
    private Coordinate3DVO rotation;

    @Schema(description = "缩放比例")
    private Coordinate3DVO scale;

    @Schema(description = "图标类型：1-塔吊，2-升降机，3-摄像头，4-环境监测点，5-喷淋设备")
    private Integer iconType;

    @Schema(description = "设备3D模型文件访问地址（/device-models/...）")
    private String modelFileUrl;
}
