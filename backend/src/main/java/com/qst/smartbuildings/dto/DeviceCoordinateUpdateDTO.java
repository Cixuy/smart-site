package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 设备3D坐标映射修改请求
 * <p>对应设计文档 3.2.4（请求体同 3.2.2 但不含 deviceId）。所有字段均可空，空值表示保留原值（部分更新）。</p>
 */
@Data
@Schema(description = "设备3D坐标映射修改请求（不含设备ID，字段可部分更新）")
public class DeviceCoordinateUpdateDTO {

    @Schema(description = "场景X坐标")
    private BigDecimal posX;

    @Schema(description = "场景Y坐标（高度）")
    private BigDecimal posY;

    @Schema(description = "场景Z坐标")
    private BigDecimal posZ;

    @Schema(description = "旋转角X（度）")
    private BigDecimal rotationX;

    @Schema(description = "旋转角Y（度）")
    private BigDecimal rotationY;

    @Schema(description = "旋转角Z（度）")
    private BigDecimal rotationZ;

    @Schema(description = "缩放比例X")
    private BigDecimal scaleX;

    @Schema(description = "缩放比例Y")
    private BigDecimal scaleY;

    @Schema(description = "缩放比例Z")
    private BigDecimal scaleZ;

    @Schema(description = "图标类型：1-塔吊，2-升降机，3-摄像头，4-环境监测点，5-喷淋设备")
    private Integer iconType;
}
