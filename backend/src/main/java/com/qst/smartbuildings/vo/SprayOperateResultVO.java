package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 启停操作结果
 */
@Data
@Schema(description = "喷淋启停操作结果")
public class    SprayOperateResultVO implements Serializable {
    @Schema(description = "操作记录ID（spray_operation_log.id）")
    private Long operationId;
    @Schema(description = "位置ID")
    private Long locationId;
    @Schema(description = "位置名称")
    private String locationName;
    @Schema(description = "触发方式：manual/auto/schedule")
    private String triggerMode;
    @Schema(description = "操作后状态：running/stopped")
    private String status;
}
