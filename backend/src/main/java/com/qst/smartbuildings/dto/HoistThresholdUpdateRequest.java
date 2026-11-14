package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改升降机阈值配置请求
 */
@Data
@Schema(description = "修改升降机阈值配置请求")
public class HoistThresholdUpdateRequest {

    @Schema(description = "升降机设备编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "HOIST001")
    @NotBlank(message = "设备编码不能为空")
    private String deviceId;

    @Schema(
            description = "指标类型",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "LOAD_WEIGHT",
            allowableValues = {"LOAD_WEIGHT", "LOAD_PERCENT", "WIND_SPEED", "HEIGHT", "PASSENGER_COUNT", "TILT_ANGLE"}
    )
    @NotBlank(message = "指标类型不能为空")
    private String metricType;

    @Schema(description = "指标类型文本", example = "载重")
    private String metricTypeText;

    @Schema(description = "预警阈值上限", requiredMode = Schema.RequiredMode.REQUIRED, example = "800")
    @NotNull(message = "预警阈值上限不能为空")
    @DecimalMin(value = "0", message = "预警阈值上限不能为负数")
    private BigDecimal thresholdWarnUpper;

    @Schema(description = "预警阈值下限", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "预警阈值下限不能为空")
    @DecimalMin(value = "0", message = "预警阈值下限不能为负数")
    private BigDecimal thresholdWarnLower;

    @Schema(description = "报警阈值上限", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "报警阈值上限不能为空")
    @DecimalMin(value = "0", message = "报警阈值上限不能为负数")
    private BigDecimal thresholdAlarmUpper;

    @Schema(description = "报警阈值下限", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "报警阈值下限不能为空")
    @DecimalMin(value = "0", message = "报警阈值下限不能为负数")
    private BigDecimal thresholdAlarmLower;

    @Schema(description = "单位", example = "kg")
    private String unit;
}