package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 设备实时指标项（详情卡片用）
 * <p>对应设计文档 3.2.3 realtimeMetrics，数据源为实时监控服务（尚未实现，暂返回空列表占位）。</p>
 */
@Data
@Schema(description = "设备实时指标项")
public class RealtimeMetricVO {

    @Schema(description = "指标名称，如 力矩百分比")
    private String name;

    @Schema(description = "指标数值")
    private BigDecimal value;

    @Schema(description = "单位，如 % / m/s")
    private String unit;

    @Schema(description = "是否处于告警状态")
    private Boolean isAlarm;
}
