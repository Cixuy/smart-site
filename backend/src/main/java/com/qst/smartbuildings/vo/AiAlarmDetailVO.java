package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiAlarmDetailVO {
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
    private Long cameraId;
    private LocalDateTime alarmTime;
    private List<String> screenshotUrls;
}
