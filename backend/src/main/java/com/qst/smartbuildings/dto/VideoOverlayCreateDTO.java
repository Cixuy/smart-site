package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 3D视频叠加配置新增请求
 * <p>对应设计文档 3.3.2。coordinateId 必填，其余有默认值。</p>
 */
@Data
@Schema(description = "3D视频叠加配置新增请求")
public class VideoOverlayCreateDTO {

    @Schema(description = "设备坐标ID（必须为摄像头类型）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "坐标ID不能为空")
    private Long coordinateId;

    @Schema(description = "视频窗口宽度（像素），默认320")
    private Integer videoWidth = 320;

    @Schema(description = "视频窗口高度（像素），默认240")
    private Integer videoHeight = 240;

    @Schema(description = "相对设备坐标的X偏移，默认0")
    private BigDecimal offsetX;

    @Schema(description = "相对设备坐标的Y偏移，默认20")
    private BigDecimal offsetY;

    @Schema(description = "是否半透明，默认true")
    private Boolean isTransparent = true;
}
