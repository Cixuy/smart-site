package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备坐标映射表 (device_coordinate_mapping)
 * <p>3D核心表，将物理设备绑定到3D场景的具体坐标。每台设备在场景中只有一个坐标。</p>
 */
@Data
@TableName("device_coordinate_mapping")
public class DeviceCoordinateMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联3D场景ID（→ scene_3d_config.id） */
    private Long sceneId;

    /** 设备ID（→ asset_device.id） */
    private Long deviceId;

    /** 场景X坐标 */
    private BigDecimal posX;

    /** 场景Y坐标（高度） */
    private BigDecimal posY;

    /** 场景Z坐标 */
    private BigDecimal posZ;

    /** 旋转角X（度） */
    private BigDecimal rotationX;

    /** 旋转角Y（度） */
    private BigDecimal rotationY;

    /** 旋转角Z（度） */
    private BigDecimal rotationZ;

    /** 缩放比例X */
    private BigDecimal scaleX;

    /** 缩放比例Y */
    private BigDecimal scaleY;

    /** 缩放比例Z */
    private BigDecimal scaleZ;

    /** 图标类型：1-塔吊，2-升降机，3-摄像头，4-环境监测点，5-喷淋设备 */
    private Integer iconType;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
