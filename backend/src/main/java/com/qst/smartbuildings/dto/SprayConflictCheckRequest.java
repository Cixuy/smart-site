package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * 定时任务时间冲突校验请求
 */
@Data
@Schema(description = "定时任务时间冲突校验请求")
public class SprayConflictCheckRequest implements Serializable {
    @Schema(description = "排除的任务ID（新增时传 0）")
    private Long excludeId;
    @Schema(description = "执行位置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;
    @Schema(description = "开始时间（HH:mm:ss）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalTime startTime;
    @Schema(description = "持续时长（分钟）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer durationMinutes;
    @Schema(description = "执行周期：daily/weekly/monthly", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cycleType;
    @Schema(description = "周期配置（JSON 字符串）")
    private String cycleConfig;
}
