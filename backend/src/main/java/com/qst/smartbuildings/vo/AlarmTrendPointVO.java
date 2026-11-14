package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AlarmTrendPointVO {
    private LocalDate periodStart;
    private Integer totalCount;
    private Integer warningCount;
    private Integer alarmCount;
    private Integer controlCount;
    private Integer pendingCount;
    private Integer resolvedCount;
}