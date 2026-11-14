package com.qst.smartbuildings.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SensorDataPushRequest {
    @NotNull @jakarta.validation.constraints.Positive private Long sensorId;
    @NotNull private BigDecimal metricValue;
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss[.SSS]", timezone = "Asia/Shanghai")
    private LocalDateTime collectTime;
}
