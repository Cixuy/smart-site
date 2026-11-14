package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EnvironmentMetric {
    private String metric;
    private double value;
    private String unit;
    @JsonProperty("isExceeded")
    private boolean exceeded;
}
