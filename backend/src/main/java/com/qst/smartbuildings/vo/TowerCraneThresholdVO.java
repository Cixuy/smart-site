package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 塔吊告警阈值配置 VO
 */
@Data
public class TowerCraneThresholdVO {
    private String deviceId;
    private String metricType;
    /** 指标类型文本（由服务层按 metric_type 推导） */
    private String metricTypeText;
    /** 指标名称（前端展示用，与 metricTypeText 同值） */
    private String metricLabel;
    /** 预警阈值上限（对应库表 warning_upper） */
    private BigDecimal thresholdWarnUpper;
    /** 预警阈值下限（对应库表 warning_lower） */
    private BigDecimal thresholdWarnLower;
    /** 报警阈值上限（对应库表 alarm_upper） */
    private BigDecimal thresholdAlarmUpper;
    /** 报警阈值下限（对应库表 alarm_lower） */
    private BigDecimal thresholdAlarmLower;
    private String unit;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
