package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DailyAlarmStatVO {
    private LocalDate statDate;
    private Integer sourceType;
    private Integer alarmLevel;
    private Integer totalCount;
    private Integer pendingCount;
    private Integer resolvedCount;
    private Integer falseAlarmCount;
    private BigDecimal avgDisposalMinutes;
    private BigDecimal maxDisposalMinutes;
}