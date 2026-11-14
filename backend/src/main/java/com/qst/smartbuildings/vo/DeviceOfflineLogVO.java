package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备离线日志查询结果（用于单设备在线率计算）。
 */
@Data
public class DeviceOfflineLogVO {

    /** 离线时间 */
    private LocalDateTime offlineTime;

    /** 上线时间（为空表示仍未恢复） */
    private LocalDateTime onlineTime;
}
