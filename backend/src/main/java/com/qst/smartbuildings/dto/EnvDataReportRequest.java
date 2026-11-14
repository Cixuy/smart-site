package com.qst.smartbuildings.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EnvDataReportRequest {
    /** Optional project context used to create/recover unified alarms automatically. */
    private Long projectId;
    @NotNull
    private Long monitorPointId;
    @NotNull
    private String metricType;
    @NotNull
    private BigDecimal metricValue;
    private String unit;
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss[.SSS]", timezone = "Asia/Shanghai")
    private LocalDateTime collectTime;
}
