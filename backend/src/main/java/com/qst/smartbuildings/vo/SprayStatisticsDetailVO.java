package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 统计分组明细
 */
@Data
@Schema(description = "喷淋统计分组明细")
public class SprayStatisticsDetailVO implements Serializable {
    @Schema(description = "分组键（日期/位置名/触发方式）")
    private String groupKey;
    @Schema(description = "启动次数")
    private Long startCount;
    @Schema(description = "运行时长（分钟）")
    private Long durationMinutes;
}
