package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

/**
 * 安全态势统计数据（工作台首页关键指标）
 */
@Data
public class SafetyStats {

    /** 在线设备数 */
    private Integer onlineDevices;

    /** 离线设备数 */
    private Integer offlineDevices;

    /** 当日告警总数 */
    private Integer dailyAlarms;

    /** 未处理告警数 */
    private Integer pendingAlarms;
}
