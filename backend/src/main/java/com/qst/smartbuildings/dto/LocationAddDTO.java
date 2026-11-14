package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增位置请求
 */
@Data
@Schema(description = "新增位置请求")
public class LocationAddDTO {

    @Schema(description = "位置名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "位置名称不能为空")
    @Size(max = 100, message = "位置名称长度不能超过100")
    private String locationName;

    @Schema(description = "父节点ID(0为根)", example = "0")
    private Long parentId;

    @Schema(description = "层级", example = "1")
    private Integer level;

    @Schema(description = "GIS经度", example = "116.4074")
    private BigDecimal gisLon;

    @Schema(description = "GIS纬度", example = "39.9042")
    private BigDecimal gisLat;

    @Schema(description = "负责人ID")
    private Long responsibleUserId;

    @Schema(description = "排序", example = "0")
    private Integer sort;
}
