package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class AlarmDistributionItemVO {
    private String key;
    private String name;
    private Long count;
    private BigDecimal percentage;
    private Map<String, String> query;
}