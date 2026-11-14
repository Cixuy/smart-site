package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 升降机实时监测数据表 (biz_hoist_realtime)
 * 存储升降机实时监测数据（载重、门状态、联锁告警等），用于升降机运行安全监控
 */
@Data
@TableName("biz_hoist_realtime")
public class BizHoistRealtime {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联设备编码 */
    private String deviceId;
    /** 采集时间（毫秒精度） */
    private LocalDateTime collectTime;
    /** 当前载重（kN） */
    private BigDecimal loadWeight;
    /** 载重百分比（%） */
    private BigDecimal loadPercent;
    /** 当前高度（m） */
    private BigDecimal height;
    /** 运行方向：0-停止，1-上升，2-下降 */
    private Integer runDirection;
    /** 前门状态：0-关，1-开 */
    private Integer frontDoorStatus;
    /** 后门状态：0-关，1-开 */
    private Integer rearDoorStatus;
    /** 当前载人数 */
    private Integer passengerCount;
    /** 风速（m/s） */
    private BigDecimal windSpeed;
    /** 联锁告警：0-正常，1-双门同开，2-超载，3-超速，4-超高 */
    private Integer interlockAlarm;
    /** 创建时间 */
    private LocalDateTime createdAt;
}