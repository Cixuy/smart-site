package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 喷淋手动启停请求
 */
@Data
@Schema(description = "喷淋手动启停请求")
public class SprayOperateRequest implements Serializable {
    @Schema(description = "执行位置ID（关联 asset_device_location）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;
    @Schema(description = "操作原因（建议必填，便于审计）")
    private String reason;
}
