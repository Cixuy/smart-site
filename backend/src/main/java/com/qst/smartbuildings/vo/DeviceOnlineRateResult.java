package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 单设备在线率结果。
 */
@Data
public class DeviceOnlineRateResult {

    /** 设备ID */
    private Long deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 统计周期总秒数 */
    private Long totalSeconds;

    /** 在线秒数 */
    private Long onlineSeconds;

    /** 离线秒数 */
    private Long offlineSeconds;

    /** 在线率(%) */
    private BigDecimal onlineRate;

    /** 离线次数 */
    private Integer offlineCount;

    /** 平均离线时长(秒) */
    private Integer avgOfflineDurationSeconds;

    /**
     * 根据统计结果计算在线率与平均离线时长。
     *
     * @param deviceId      设备ID
     * @param deviceName    设备名称
     * @param totalSeconds  统计周期总秒数
     * @param onlineSeconds 在线秒数
     * @param offlineSeconds 离线秒数
     * @param offlineCount  离线次数
     */
    public DeviceOnlineRateResult(Long deviceId, String deviceName, long totalSeconds,
                                  long onlineSeconds, long offlineSeconds, int offlineCount) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.totalSeconds = totalSeconds;
        this.offlineSeconds = offlineSeconds;
        long safeOnline = Math.max(0, onlineSeconds);
        this.onlineSeconds = safeOnline;
        this.offlineCount = offlineCount;
        this.onlineRate = totalSeconds <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(safeOnline)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalSeconds), 2, RoundingMode.HALF_UP);
        this.avgOfflineDurationSeconds = offlineCount == 0
                ? 0
                : (int) Math.round(offlineSeconds / (double) offlineCount);
    }
}
