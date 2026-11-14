package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 喷淋运行统计请求
 */
@Data
@Schema(description = "喷淋运行统计请求")
public class SprayStatisticsRequest implements Serializable {
    @Schema(description = "位置ID，不传则全局统计")
    private Long locationId;
    @Schema(description = "开始时间（yyyy-MM-dd HH:mm:ss）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String beginTime;
    @Schema(description = "结束时间（yyyy-MM-dd HH:mm:ss）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endTime;
    @Schema(description = "分组维度：day/location/triggerMode")
    private String groupBy;
}
