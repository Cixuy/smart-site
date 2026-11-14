package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 升降机实时监测数据 VO（最新数据 / 历史曲线 / 联锁告警列表共用）
 */
@Data
@Schema(description = "升降机实时监测数据")
public class HoistRealtimeVO {
    private Long id;
    private String deviceId;
    /** 设备名称（来自设备台账） */
    private String deviceName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private LocalDateTime collectTime;
    private BigDecimal loadWeight;
    private BigDecimal loadPercent;
    private BigDecimal height;
    private Integer runDirection;
    /** 运行方向文本：0-停止，1-上升，2-下降 */
    private String runDirectionText;
    private Integer frontDoorStatus;
    /** 前门状态文本：0-关，1-开 */
    private String frontDoorStatusText;
    private Integer rearDoorStatus;
    /** 后门状态文本：0-关，1-开 */
    private String rearDoorStatusText;
    private Integer passengerCount;
    private BigDecimal windSpeed;
    private Integer interlockAlarm;
    /** 联锁告警文本：0-正常，1-双门同开，2-超载，3-超速，4-超高 */
    private String interlockAlarmText;
}