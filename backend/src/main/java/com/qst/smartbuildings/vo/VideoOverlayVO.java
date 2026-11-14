package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 3D视频叠加配置视图
 * <p>对应设计文档 3.3.1 响应。streamUrl 来自摄像头管理模块（biz_camera 尚未实现，暂置 null）。</p>
 */
@Data
@Schema(description = "3D视频叠加配置视图")
public class VideoOverlayVO {

    @Schema(description = "叠加配置ID")
    private Long id;

    @Schema(description = "设备坐标ID")
    private Long coordinateId;

    @Schema(description = "视频窗口宽度（像素）")
    private Integer videoWidth;

    @Schema(description = "视频窗口高度（像素）")
    private Integer videoHeight;

    @Schema(description = "相对设备坐标的X偏移")
    private BigDecimal offsetX;

    @Schema(description = "相对设备坐标的Y偏移")
    private BigDecimal offsetY;

    @Schema(description = "是否半透明：true-是，false-否")
    private Boolean isTransparent;

    @Schema(description = "视频流地址（摄像头模块提供，暂置null）")
    private String streamUrl;
}
