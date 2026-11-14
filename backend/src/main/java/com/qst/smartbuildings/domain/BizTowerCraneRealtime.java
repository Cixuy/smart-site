package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 塔吊实时监测数据表 (biz_tower_crane_realtime)
 * 存储塔吊实时监测数据（吊重、力矩、风速等），用于塔吊运行状态监控与超载/超风速告警
 */
@Data
@TableName("biz_tower_crane_realtime")
public class BizTowerCraneRealtime {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联设备编码 */
    private String deviceId;
    /** 采集时间（毫秒精度） */
    private LocalDateTime collectTime;
    /** 当前吊重（kN） */
    private BigDecimal loadWeight;
    /** 幅度（m） */
    private BigDecimal amplitude;
    /** 力矩（kN·m） */
    private BigDecimal moment;
    /** 力矩百分比（%） */
    private BigDecimal momentPercent;
    /** 风速（m/s） */
    private BigDecimal windSpeed;
    /** 吊钩高度（m），允许负值 */
    private BigDecimal height;
    /** 回转角度（°） */
    private BigDecimal slewAngle;
    /** 状态：0-正常，1-预警，2-报警 */
    private Integer alarmStatus;
    /** 创建时间 */
    private LocalDateTime createdAt;
}