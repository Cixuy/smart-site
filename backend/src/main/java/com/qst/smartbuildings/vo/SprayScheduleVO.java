package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 定时任务视图
 */
@Data
@Schema(description = "喷淋定时任务视图")
public class SprayScheduleVO implements Serializable {
    private Long id;
    private String taskName;
    private Long locationId;
    private String locationName;
    @Schema(description = "开始时间（HH:mm:ss）")
    private String startTime;
    private Integer durationMinutes;
    private String cycleType;
    private String cycleConfig;
    @Schema(description = "状态：1-启用，0-禁用")
    private Integer status;
    private String remark;
    private String createTime;
    private String updateTime;
}
