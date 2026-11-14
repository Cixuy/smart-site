package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改位置请求
 */
@Data
@Schema(description = "修改位置请求")
public class LocationUpdateDTO {

    @Schema(description = "位置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "位置ID不能为空")
    private Long id;

    @Schema(description = "位置名称")
    @Size(max = 100, message = "位置名称长度不能超过100")
    private String locationName;

    @Schema(description = "GIS经度")
    private BigDecimal gisLon;

    @Schema(description = "GIS纬度")
    private BigDecimal gisLat;

    @Schema(description = "负责人ID")
    private Long responsibleUserId;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态：1-启用，0-禁用")
    private Integer status;
}
