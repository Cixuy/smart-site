package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 塔吊作业记录表 (biz_tower_crane_work_record)
 * 存储塔吊一次完整作业的记录（起止时间、平均/最大吊重等），支撑作业效率与安全分析
 */
@Data
@TableName("biz_tower_crane_work_record")
public class BizTowerCraneWorkRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联设备编码 */
    private String deviceId;
    /** 作业开始时间 */
    private LocalDateTime startTime;
    /** 作业结束时间 */
    private LocalDateTime endTime;
    /** 平均吊重（kN） */
    private BigDecimal avgLoad;
    /** 最大吊重（kN） */
    private BigDecimal maxLoad;
    /** 最大吊重占比（%） */
    private BigDecimal maxLoadRatio;
    /** 最大风速（m/s） */
    private BigDecimal maxWindSpeed;
    /** 最大高度（m） */
    private BigDecimal maxHeight;
    /** 吊点半径（m） */
    private BigDecimal hookRadius;
    /** 卸料位置描述 */
    private String unloadPosition;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记 */
    private Integer isDeleted;
}