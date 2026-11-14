package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "离线统计")
public class OfflineLogStatsVO {

    @Schema(description = "离线次数")
    private Long offlineCount;

    @Schema(description = "总离线时长（分钟）")
    private Integer totalDurationMinutes;

    @Schema(description = "平均离线时长（分钟，保留1位小数）")
    private Double avgDurationMinutes;

    @Schema(description = "最长单次离线时长（分钟）")
    private Integer maxDurationMinutes;
}
