package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class AlarmDimensionCountVO {
    private String dimension;
    private String label;
    private Long count;
    private BigDecimal percentage;
    private Map<String, String> query;
}