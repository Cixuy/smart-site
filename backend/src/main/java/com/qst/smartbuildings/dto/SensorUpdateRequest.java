package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SensorUpdateRequest {
    @NotNull private Long id;
    @Size(max = 50) private String sensorName;
    @Size(max = 30) private String monitorType;
    @Size(max = 10) private String unit;
    private BigDecimal warningUpper;
    private BigDecimal warningLower;
    private BigDecimal alarmUpper;
    private BigDecimal alarmLower;
    private BigDecimal sprayThreshold;
    @Min(1) @Max(86400) private Integer refreshRate;
}
