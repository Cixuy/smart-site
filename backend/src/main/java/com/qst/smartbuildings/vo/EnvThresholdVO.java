package com.qst.smartbuildings.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class EnvThresholdVO {
    private Long sensorId;
    private Long monitorPointId;
    private String sensorName;
    private String metricType;
    private String unit;
    private BigDecimal warningUpper;
    private BigDecimal warningLower;
    private BigDecimal alarmUpper;
    private BigDecimal alarmLower;
    private BigDecimal sprayThreshold;
}