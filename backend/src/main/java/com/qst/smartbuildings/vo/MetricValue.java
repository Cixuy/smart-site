package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MetricValue {
    private String name;
    private double value;
    private String unit;
    @JsonProperty("isAlarm")
    private boolean alarm;
}
