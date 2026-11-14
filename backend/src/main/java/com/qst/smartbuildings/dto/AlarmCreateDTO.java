package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(name = "AlarmCreateDTO", description = "统一告警生成请求")
public class AlarmCreateDTO {
    @NotNull @Positive
    private Long projectId;
    @NotNull @Schema(description = "告警来源：1-设备监测，2-环境监测，3-AI识别")
    private Integer sourceType;
    @NotNull @Schema(description = "告警级别：1-预警，2-警报，3-控制")
    private Integer alarmLevel;
    @NotBlank @Size(max = 30)
    private String alarmTypeCode;
    @NotBlank @Size(max = 500)
    private String alarmContent;
    private BigDecimal currentValue;
    @Size(max = 100)
    private String thresholdRange;
    private Long deviceId;
    private Long monitorPointId;
    @Min(0) @Max(2) private Integer monitorPointParentType;
    private Long cameraId;
    private Long relatedAlarmId;
    private LocalDateTime alarmTime;
}
