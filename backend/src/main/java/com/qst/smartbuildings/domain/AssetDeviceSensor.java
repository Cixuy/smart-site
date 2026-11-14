package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("asset_device_sensor")
public class AssetDeviceSensor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sensorName;
    @TableField("parent_id")
    private Long parentId;
    @TableField("parent_type")
    private Integer parentType;
    private String monitorType;
    private String unit;
    private BigDecimal warningUpper;
    private BigDecimal warningLower;
    private BigDecimal alarmUpper;
    private BigDecimal alarmLower;
    private BigDecimal sprayThreshold;
    private Integer refreshRate;
    private Integer status;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
