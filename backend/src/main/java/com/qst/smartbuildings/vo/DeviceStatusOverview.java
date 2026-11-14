package com.qst.smartbuildings.vo;

import lombok.Data;

@Data
public class DeviceStatusOverview {
    private int onlineCount;
    private int offlineCount;
    private int faultCount;
}
