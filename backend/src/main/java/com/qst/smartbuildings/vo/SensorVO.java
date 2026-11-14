package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SensorVO {
    private Long id;
    private String sensorName;
    private Long parentId;
    private Integer parentType;
    private String parentName;
    private String monitorType;
    private String unit;
    private BigDecimal warningUpper;
    private BigDecimal warningLower;
    private BigDecimal alarmUpper;
    private BigDecimal alarmLower;
    private BigDecimal sprayThreshold;
    private Integer refreshRate;
    private Integer status;
    private BigDecimal lastValue;
    private LocalDateTime lastCollectTime;
    private LocalDateTime createTime;
}
