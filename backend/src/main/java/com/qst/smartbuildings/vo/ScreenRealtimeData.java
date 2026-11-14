package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ScreenRealtimeData {
    private DeviceStatusOverview deviceStatus = new DeviceStatusOverview();
    private List<AlarmSummary> alarmScroll = new ArrayList<>();
    private List<EquipmentRuntimeStatus> towerCraneStatus = new ArrayList<>();
    private List<EquipmentRuntimeStatus> hoistStatus = new ArrayList<>();
    private List<EnvironmentMetric> environmentMetrics = new ArrayList<>();
    private List<TrendPoint> alarmTrend = new ArrayList<>();
    private List<ScreenVideoRotationVO> videoRotations = new ArrayList<>();
    private OffsetDateTime serverTime;
}
