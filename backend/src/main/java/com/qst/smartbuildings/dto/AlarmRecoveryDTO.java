package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(name = "AlarmRecoveryDTO", description = "告警自动恢复记录请求")
public class AlarmRecoveryDTO {
    @NotNull @Positive
    private Long alarmId;
    private LocalDateTime recoveryTime;
    private BigDecimal recoveryValue;
    @Size(max = 200)
    private String recoveryNote;
}
