package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_env_monitor_data")
public class EnvMonitorData {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("parent_id")
    private Long monitorPointId;
    @TableField(exist = false)
    private String deviceId;
    private LocalDateTime collectTime;
    private String metricType;
    private BigDecimal metricValue;
    private String unit;
    private Integer status;
    private LocalDateTime createdAt;

    /** Response-only snapshots of the thresholds used for presentation. */
    @TableField(exist = false)
    private BigDecimal thresholdWarn;
    @TableField(exist = false)
    private BigDecimal thresholdAlarm;
}
