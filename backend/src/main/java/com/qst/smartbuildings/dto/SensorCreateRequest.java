package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SensorCreateRequest {
    @NotBlank @Size(max = 50) private String sensorName;
    @Positive private Long parentId;
    @NotNull @Min(0) @Max(3) private Integer parentType;
    @NotBlank @Size(max = 30) private String monitorType;
    @NotBlank @Size(max = 10) private String unit;
    private BigDecimal warningUpper;
    private BigDecimal warningLower;
    @NotNull private BigDecimal alarmUpper;
    private BigDecimal alarmLower;
    private BigDecimal sprayThreshold;
    @Min(1) @Max(86400) private Integer refreshRate = 5;
}
