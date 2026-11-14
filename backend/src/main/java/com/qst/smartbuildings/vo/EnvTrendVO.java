package com.qst.smartbuildings.vo;

import lombok.Data;

import java.util.List;

@Data
public class EnvTrendVO {
    private Long monitorPointId;
    private String pointName;
    private String metricType;
    private String unit;
    private List<EnvTrendPointVO> trend;
}
