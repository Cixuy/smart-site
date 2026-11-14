package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class EnvThresholdUpdateRequest {
    @NotNull @Positive
    private Long sensorId;
    private BigDecimal warningUpper;
    private BigDecimal warningLower;
    private BigDecimal alarmUpper;
    private BigDecimal alarmLower;
    private BigDecimal sprayThreshold;
}