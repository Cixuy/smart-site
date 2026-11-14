package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 喷淋定时任务表 (spray_schedule_task)
 * 支持制定常规性的除尘计划，支持冲突校验。
 */
@Data
@TableName("spray_schedule_task")
public class SprayScheduleTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称 */
    private String taskName;

    /** 执行位置ID */
    private Long locationId;

    /** 开始时间（HH:mm:ss，对应数据库 TIME 类型） */
    private LocalTime startTime;

    /** 持续时长（分钟） */
    private Integer durationMinutes;

    /** 执行周期：daily-每天，weekly-每周，monthly-每月 */
    private String cycleType;

    /** 周期配置（JSON 字符串，如 weekly: ["MON","WED"]；monthly: [1,15]） */
    private String cycleConfig;

    /** 状态：1-启用，0-禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 逻辑删除：0-未删，1-删除 */
    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
