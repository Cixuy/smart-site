package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 设备监测点视图对象，对应表 asset_device_sensor。用于设备详情/列表展示设备关联的传感器。
 */
@Data
public class DeviceSensorVO {

    private Long id;

    /** 监测点名称（如：塔吊1-风速） */
    private String sensorName;

    /** 所属设备ID或监测点ID */
    private Long parentId;

    /** 监测类型（wind_speed, load, pm25等） */
    private String monitorType;

    /** 单位（m/s, kN等） */
    private String unit;

    /** 预警上限 */
    private BigDecimal warningUpper;

    /** 预警下限 */
    private BigDecimal warningLower;

    /** 报警上限 */
    private BigDecimal alarmUpper;

    /** 报警下限 */
    private BigDecimal alarmLower;

    /** 喷淋联动阈值（仅环境类） */
    private BigDecimal sprayThreshold;

    /** 刷新频率（秒） */
    private Integer refreshRate;

    /** 状态：1-运行，0-停止，2-故障 */
    private Integer status;
}
