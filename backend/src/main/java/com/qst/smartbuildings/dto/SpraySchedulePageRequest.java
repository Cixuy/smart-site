package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 定时任务分页请求
 */
@Data
@Schema(description = "定时任务分页请求")
public class SpraySchedulePageRequest implements Serializable {
    @Schema(description = "当前页码，默认1")
    private Integer pageNum = 1;
    @Schema(description = "每页条数，默认20")
    private Integer pageSize = 20;
    @Schema(description = "任务名称（模糊）")
    private String taskName;
    @Schema(description = "执行位置ID")
    private Long locationId;
    @Schema(description = "执行周期：daily/weekly/monthly")
    private String cycleType;
    @Schema(description = "状态：1-启用，0-禁用")
    private Integer status;
}
