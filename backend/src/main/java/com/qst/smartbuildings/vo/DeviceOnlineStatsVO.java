package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "设备在线率统计")
public class DeviceOnlineStatsVO {

    @Schema(description = "设备总数")
    private Integer totalCount;

    @Schema(description = "在线设备数")
    private Integer onlineCount;

    @Schema(description = "离线设备数")
    private Integer offlineCount;

    @Schema(description = "已禁用设备数")
    private Integer disabledCount;

    @Schema(description = "在线率（百分比，保留1位小数）")
    private Double onlineRate;
}
