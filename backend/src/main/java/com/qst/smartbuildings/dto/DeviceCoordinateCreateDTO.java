package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 设备3D坐标映射新增请求
 * <p>对应设计文档 3.2.2。deviceId、坐标（posX/Y/Z）、iconType 必填。</p>
 */
@Data
@Schema(description = "设备3D坐标映射新增请求")
public class DeviceCoordinateCreateDTO {

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备ID不能为空")
    private Long deviceId;

    @Schema(description = "场景X坐标", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场景X坐标不能为空")
    private BigDecimal posX;

    @Schema(description = "场景Y坐标（高度）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场景Y坐标不能为空")
    private BigDecimal posY;

    @Schema(description = "场景Z坐标", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场景Z坐标不能为空")
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

    @Schema(description = "图标类型：1-塔吊，2-升降机，3-摄像头，4-环境监测点，5-喷淋设备",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "图标类型不能为空")
    private Integer iconType;
}
