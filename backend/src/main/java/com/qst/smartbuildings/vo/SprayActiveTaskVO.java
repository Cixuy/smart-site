package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 启用中的定时任务（含下次执行时间，供调度器/前端展示）
 */
@Data
@Schema(description = "启用中的定时任务视图")
public class SprayActiveTaskVO implements Serializable {
    private Long id;
    private String taskName;
    private Long locationId;
    private String locationName;
    @Schema(description = "开始时间（HH:mm:ss）")
    private String startTime;
    private Integer durationMinutes;
    private String cycleType;
    private String cycleConfig;
    @Schema(description = "下次执行时间（yyyy-MM-dd HH:mm:ss）")
    private String nextExecuteTime;
}
