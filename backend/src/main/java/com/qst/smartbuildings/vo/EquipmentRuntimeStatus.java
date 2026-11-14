package com.qst.smartbuildings.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EquipmentRuntimeStatus {
    private Long deviceId;
    private String deviceName;
    private Integer onlineStatus;
    private List<MetricValue> metrics = new ArrayList<>();
}
