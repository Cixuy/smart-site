package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 设备3D详情卡片视图
 * <p>对应设计文档 3.2.3 响应。realtimeMetrics / alarms 依赖实时监控与告警模块，暂返回空列表占位。</p>
 */
@Data
@Schema(description = "设备3D详情卡片视图")
public class DeviceDetailVO {

    @Schema(description = "坐标映射ID")
    private Long coordinateId;

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "设备编号（设备台账模块提供，暂占位）")
    private String deviceCode;

    @Schema(description = "设备名称（设备台账模块提供，暂占位）")
    private String deviceName;

    @Schema(description = "在线状态：1-在线，0-离线（暂置1）")
    private Integer onlineStatus;

    @Schema(description = "启用状态：1-启用，0-停用（暂置1）")
    private Integer enableStatus;

    @Schema(description = "最新告警级别：warning/alarm/control（暂置null）")
    private String latestAlarmLevel;

    @Schema(description = "三维坐标")
    private Coordinate3DVO position;

    @Schema(description = "图标类型：1-塔吊，2-升降机，3-摄像头，4-环境监测点，5-喷淋设备")
    private Integer iconType;

    @Schema(description = "设备3D模型文件访问地址（/device-models/...）")
    private String modelFileUrl;

    @Schema(description = "实时指标列表（实时监控服务，暂空）")
    private List<RealtimeMetricVO> realtimeMetrics;

    @Schema(description = "告警列表（告警模块，暂空）")
    private List<AlarmItemVO> alarms;

    @Schema(description = "详情页跳转地址")
    private String detailUrl;
}
