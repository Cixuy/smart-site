package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 升降机作业记录表 (biz_hoist_work_record)
 * 存储升降机一次完整作业的记录（起止楼层、最大载人等），支撑垂直运输效率分析
 */
@Data
@TableName("biz_hoist_work_record")
public class BizHoistWorkRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联设备编码 */
    private String deviceId;
    /** 作业开始时间 */
    private LocalDateTime startTime;
    /** 作业结束时间 */
    private LocalDateTime endTime;
    /** 平均载重（kN） */
    private BigDecimal avgLoad;
    /** 最大载重（kN） */
    private BigDecimal maxLoad;
    /** 最大载人数 */
    private Integer maxPassengers;
    /** 起止楼层（如 "1F-15F"） */
    private String floorRange;
    /** 平均风速（m/s） */
    private BigDecimal avgWindSpeed;
    /** 最大倾斜角（°） */
    private BigDecimal maxTiltAngle;
    /** 高度变化范围（m） */
    private BigDecimal heightRange;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记 */
    private Integer isDeleted;
}