package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OperatorTimelinessVO {
    private Long operatorId;
    private String operatorName;
    private Integer disposedCount;
    private Integer timelyCount;
    private Integer overdueCount;
    private BigDecimal timelyRate;
    private BigDecimal avgDisposalMinutes;
    private BigDecimal maxDisposalMinutes;
}