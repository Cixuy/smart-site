package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EnvMonitorRecordVO {
    private Long id;
    private Long monitorPointId;
    private String pointName;
    private String deviceId;
    private LocalDateTime collectTime;
    private String metricType;
    private String metricTypeText;
    private BigDecimal metricValue;
    private String unit;
    private BigDecimal thresholdWarn;
    private BigDecimal thresholdAlarm;
    private Integer status;
    private String statusText;
}
