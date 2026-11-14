package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 传感器静态元数据（用于实时推流渲染）。前端在建立 WebSocket 连接前/后拉取一次，
 * 后续 WS 推送仅含精简字段，前端通过 id 匹配本对象。
 */
@Data
public class SensorMetaVO {

    @Schema(description = "传感器ID（与WS推送的id对应）", example = "1")
    private Long id;

    @Schema(description = "传感器名称", example = "塔吊1-风速")
    private String sensorName;

    @Schema(description = "监测类型", example = "wind_speed")
    private String monitorType;

    @Schema(description = "单位", example = "m/s")
    private String unit;

    @Schema(description = "所属设备/监测点ID", example = "100")
    private Long parentId;

    @Schema(description = "父节点类型：1-设备，2-监测点", example = "1")
    private Integer parentType;

    @Schema(description = "预警上限", example = "10")
    private BigDecimal warningUpper;

    @Schema(description = "预警下限", example = "0")
    private BigDecimal warningLower;

    @Schema(description = "报警上限", example = "15")
    private BigDecimal alarmUpper;

    @Schema(description = "报警下限", example = "null")
    private BigDecimal alarmLower;

    @Schema(description = "刷新频率(秒)", example = "5")
    private Integer refreshRate;

    @Schema(description = "状态：1-运行，0-停止，2-故障", example = "1")
    private Integer status;
}