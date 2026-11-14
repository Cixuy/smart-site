package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * GIS坐标数据 VO（用于地图/3D可视化打点）
 */
@Data
public class LocationMapVO {
    private Long id;
    private String locationName;
    private BigDecimal gisLon;
    private BigDecimal gisLat;
    private Integer level;
    /** 该位置下的设备数量 */
    private Long deviceCount;
}
