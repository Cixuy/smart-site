package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AlarmListVO {
    private Long id;
    private String alarmNo;
    private Long projectId;
    private Integer sourceType;
    private String sourceTypeText;
    private Integer alarmLevel;
    private String alarmLevelText;
    private String alarmTypeCode;
    private String alarmTypeText;
    private String alarmContent;
    private BigDecimal currentValue;
    private String thresholdRange;
    private Integer status;
    private String statusText;
    private Long deviceTypeId;
    private String deviceTypeName;
    private Long deviceId;
    private String deviceName;
    private Long monitorPointId;
    private Integer monitorPointParentType;
    private String monitorPointName;
    private Long cameraId;
    private String cameraName;
    private Long relatedAlarmId;
    private Integer relatedCount;
    private LocalDateTime alarmTime;
    private Boolean recovered;
    private LocalDateTime recoveryTime;
}