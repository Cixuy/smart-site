package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EnvDataPushResult {
    private Long id;
    private Long monitorPointId;
    private Integer parentType;
    private boolean alarmTriggered;
    private Integer alarmLevel;
    private String alarmLevelText;

    public EnvDataPushResult(Long id, boolean alarmTriggered, Integer alarmLevel, String alarmLevelText) {
        this(id, null, alarmTriggered, alarmLevel, alarmLevelText);
    }

    public EnvDataPushResult(Long id, Long monitorPointId, boolean alarmTriggered, Integer alarmLevel, String alarmLevelText) {
        this.parentType = 2;
        this.id = id;
        this.monitorPointId = monitorPointId;
        this.alarmTriggered = alarmTriggered;
        this.alarmLevel = alarmLevel;
        this.alarmLevelText = alarmLevelText;
    }
}