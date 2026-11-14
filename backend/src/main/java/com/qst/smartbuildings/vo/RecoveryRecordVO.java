package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecoveryRecordVO {
    private Long id;
    private Long alarmId;
    private LocalDateTime recoveryTime;
    private BigDecimal recoveryValue;
    private String recoveryNote;
}