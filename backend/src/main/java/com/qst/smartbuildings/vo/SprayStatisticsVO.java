package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 喷淋运行统计结果
 */
@Data
@Schema(description = "喷淋运行统计结果")
public class SprayStatisticsVO implements Serializable {
    @Schema(description = "总启动次数")
    private Long totalStartCount;
    @Schema(description = "总运行时长（分钟）")
    private Long totalDurationMinutes;
    @Schema(description = "平均单次时长（分钟）")
    private Long avgDurationMinutes;
    @Schema(description = "自动联动占比（%）")
    private BigDecimal autoTriggerRate;
    @Schema(description = "手动占比（%）")
    private BigDecimal manualTriggerRate;
    @Schema(description = "定时占比（%）")
    private BigDecimal scheduleTriggerRate;
    private List<SprayStatisticsDetailVO> details;
}
