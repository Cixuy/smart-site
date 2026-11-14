package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 自动联动触发请求（由环境监测服务在 PM2.5/PM10 超标时调用）
 */
@Data
@Schema(description = "自动联动触发请求")
public class SprayTriggerRequest implements Serializable {
    @Schema(description = "执行位置ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;
    @Schema(description = "触发联动的监测点ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sensorId;
    @Schema(description = "触发时的超标数值（与监测点 spray_threshold 比较）")
    private BigDecimal metricValue;
}
