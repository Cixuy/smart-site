package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlarmPushLogVO {
    private Long id;
    private Long alarmId;
    private Integer pushChannel;
    private String pushTarget;
    private Integer pushStatus;
    private LocalDateTime pushTime;
    private String responseCode;
    private String responseMsg;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}