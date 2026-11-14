package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 3D视频叠加配置表 (scene_video_overlay)
 * <p>存储摄像头视频在3D场景中的悬浮窗位置参数。</p>
 */
@Data
@TableName("scene_video_overlay")
public class SceneVideoOverlay {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联设备坐标ID（必须为摄像头类型）→ device_coordinate_mapping.id */
    private Long coordinateId;

    /** 视频窗口宽度（像素） */
    private Integer videoWidth;

    /** 视频窗口高度（像素） */
    private Integer videoHeight;

    /** 相对设备坐标的X偏移 */
    private BigDecimal offsetX;

    /** 相对设备坐标的Y偏移 */
    private BigDecimal offsetY;

    /** 是否半透明：1-是，0-否 */
    private Integer isTransparent;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
