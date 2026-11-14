package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 设备在线率统计结果。
 */
@Data
public class OnlineStatsResult {

    /** 设备总数 */
    private Long totalCount;

    /** 在线设备数 */
    private Long onlineCount;

    /** 离线设备数 */
    private Long offlineCount;

    /** 禁用设备数 */
    private Long disabledCount;

    /** 在线率(%) */
    private BigDecimal onlineRate;

    /**
     * 构造统计结果并计算在线率。
     *
     * @param totalCount     设备总数
     * @param onlineCount    在线设备数
     * @param offlineCount   离线设备数
     * @param disabledCount  禁用设备数
     */
    public OnlineStatsResult(long totalCount, long onlineCount, long offlineCount, long disabledCount) {
        this.totalCount = totalCount;
        this.onlineCount = onlineCount;
        this.offlineCount = offlineCount;
        this.disabledCount = disabledCount;
        this.onlineRate = totalCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(onlineCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
    }
}
