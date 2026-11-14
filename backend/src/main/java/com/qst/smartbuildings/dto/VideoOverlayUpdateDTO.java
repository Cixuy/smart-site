package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 3D视频叠加配置修改请求
 * <p>对应设计文档 3.3.3（请求体同 3.3.2）。字段均可空，空值保留原值。</p>
 */
@Data
@Schema(description = "3D视频叠加配置修改请求")
public class VideoOverlayUpdateDTO {

    @Schema(description = "设备坐标ID（必须为摄像头类型）")
    private Long coordinateId;

    @Schema(description = "视频窗口宽度（像素）")
    private Integer videoWidth;

    @Schema(description = "视频窗口高度（像素）")
    private Integer videoHeight;

    @Schema(description = "相对设备坐标的X偏移")
    private BigDecimal offsetX;

    @Schema(description = "相对设备坐标的Y偏移")
    private BigDecimal offsetY;

    @Schema(description = "是否半透明")
    private Boolean isTransparent;
}
