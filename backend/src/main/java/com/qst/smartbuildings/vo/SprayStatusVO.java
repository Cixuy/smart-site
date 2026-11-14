package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 区域喷淋运行状态
 */
@Data
@Schema(description = "区域喷淋运行状态")
public class SprayStatusVO implements Serializable {
    @Schema(description = "位置ID")
    private Long locationId;
    @Schema(description = "位置名称")
    private String locationName;
    @Schema(description = "是否运行中")
    private Boolean isRunning;
    @Schema(description = "最近一次启动时间（yyyy-MM-dd HH:mm:ss，无 +08:00）")
    private String lastStartTime;
    @Schema(description = "当前触发方式：auto/manual/schedule（未运行时为 null）")
    private String triggerMode;
}
