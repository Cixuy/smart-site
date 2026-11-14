package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AlarmTimelinessVO {
    private Integer deadlineMinutes;
    private Integer disposedCount;
    private Integer timelyCount;
    private Integer overdueCount;
    private BigDecimal timelyRate;
    private List<OperatorTimelinessVO> operators;
}