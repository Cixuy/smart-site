package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EnvTrendPointVO {
    private LocalDate date;
    private BigDecimal maxValue;
    private BigDecimal minValue;
    private BigDecimal avgValue;
}
