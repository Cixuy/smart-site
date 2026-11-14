package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_alarm_record")
public class AlarmRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alarmNo;
    private Long projectId;
    private Integer sourceType;
    private Integer alarmLevel;
    private String alarmTypeCode;
    private String alarmContent;
    private BigDecimal currentValue;
    private String thresholdRange;
    private Integer status;
    private Long deviceId;
    private Long monitorPointId;
    private Long cameraId;
    private Long relatedAlarmId;
    private LocalDateTime alarmTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
