package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 3D场景告警高亮设备视图
 * <p>对应设计文档 3.2.6 响应。数据源为告警管理模块 biz_alarm_record（尚未实现，暂返回空列表占位）。</p>
 */
@Data
@Schema(description = "3D场景告警高亮设备视图")
public class AlarmDeviceVO {

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "坐标映射ID")
    private Long coordinateId;

    @Schema(description = "告警级别：warning/alarm/control（按最高级别）")
    private String alarmLevel;

    @Schema(description = "闪烁频率，次/秒")
    private Integer blinkFrequency;

    @Schema(description = "三维坐标（用于前端定位闪烁）")
    private Coordinate3DVO position;
}
