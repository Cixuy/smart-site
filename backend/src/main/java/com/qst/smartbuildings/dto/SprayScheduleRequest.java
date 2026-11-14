package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * 喷淋定时任务新增/修改请求
 */
@Data
@Schema(description = "喷淋定时任务请求")
public class SprayScheduleRequest implements Serializable {
    @Schema(description = "任务ID（修改时必填，新增时不传）")
    private Long id;
    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskName;
    @Schema(description = "执行位置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;
    @Schema(description = "开始时间（HH:mm:ss）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalTime startTime;
    @Schema(description = "持续时长（分钟）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer durationMinutes;
    @Schema(description = "执行周期：daily/weekly/monthly", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cycleType;
    @Schema(description = "周期配置（JSON 字符串，weekly 如 [\"MON\",\"WED\"]，monthly 如 [1,15]）")
    private String cycleConfig;
    @Schema(description = "备注")
    private String remark;
}
