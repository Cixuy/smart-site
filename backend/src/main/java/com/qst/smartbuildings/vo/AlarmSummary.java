package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlarmSummary {
    private Long alarmId;
    private String alarmType;
    private String alarmLevel;
    private Long deviceId;
    private String deviceName;
    private LocalDateTime alarmTime;
}
