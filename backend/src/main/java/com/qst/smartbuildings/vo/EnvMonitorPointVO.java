package com.qst.smartbuildings.vo;

import lombok.Data;
import java.util.List;

@Data
public class EnvMonitorPointVO {
    private Long id;
    private String name;
    private String location;
    private long sensorCount;
    private List<Long> sensorIds;
}
