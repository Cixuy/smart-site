package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 塔吊作业记录 VO
 */
@Data
public class TowerCraneWorkRecordVO {
    private Long id;
    private String deviceId;
    /** 设备名称（来自设备台账） */
    private String deviceName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
    private BigDecimal avgLoad;
    private BigDecimal maxLoad;
    private BigDecimal maxLoadRatio;
    private BigDecimal maxWindSpeed;
    private BigDecimal maxHeight;
    private BigDecimal hookRadius;
    private String unloadPosition;
}