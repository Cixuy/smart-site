package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "单设备在线率")
public class DeviceOnlineRateVO {

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "统计周期总秒数")
    private Long totalSeconds;

    @Schema(description = "在线秒数")
    private Long onlineSeconds;

    @Schema(description = "离线秒数")
    private Long offlineSeconds;

    @Schema(description = "在线率（百分比，保留1位小数）")
    private Double onlineRate;

    @Schema(description = "离线次数")
    private Integer offlineCount;

    @Schema(description = "平均离线时长（秒）")
    private Long avgOfflineDurationSeconds;
}
