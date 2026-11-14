package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 塔吊实时监测数据 VO（最新数据 / 历史曲线 / 告警列表共用）
 */
@Data
@Schema(description = "塔吊实时监测数据")
public class TowerCraneRealtimeVO {
    private Long id;
    private String deviceId;
    /** 设备名称（来自设备台账） */
    private String deviceName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private LocalDateTime collectTime;
    private BigDecimal loadWeight;
    private BigDecimal amplitude;
    private BigDecimal moment;
    private BigDecimal momentPercent;
    private BigDecimal windSpeed;
    private BigDecimal height;
    private BigDecimal slewAngle;
    private Integer alarmStatus;
    /** 告警状态文本：0-正常，1-预警，2-报警 */
    private String alarmStatusText;
}