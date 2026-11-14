package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EnvDailyStatVO {
    private Long id;
    private Long monitorPointId;
    private String pointName;
    private LocalDate statDate;
    private String metricType;
    private String metricTypeText;
    private BigDecimal maxValue;
    private BigDecimal minValue;
    private BigDecimal avgValue;
    private Integer alarmCount;
    private Integer warnCount;
}
