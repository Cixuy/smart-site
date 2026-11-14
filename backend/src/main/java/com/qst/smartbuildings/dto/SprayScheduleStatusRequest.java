package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 定时任务启用/禁用请求
 */
@Data
@Schema(description = "定时任务启用/禁用请求")
public class SprayScheduleStatusRequest implements Serializable {
    @Schema(description = "状态：1-启用，0-禁用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;
}
