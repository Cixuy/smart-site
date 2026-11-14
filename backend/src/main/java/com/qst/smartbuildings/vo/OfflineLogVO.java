package com.qst.smartbuildings.vo;

import com.qst.smartbuildings.domain.OfflineLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
@Schema(description = "离线记录")
public class OfflineLogVO {

    @Schema(description = "离线记录ID")
    private Long id;

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "设备名称（冗余）")
    private String deviceName;

    @Schema(description = "设备编码（冗余）")
    private String deviceCode;

    @Schema(description = "离线开始时间")
    private LocalDateTime offlineTime;

    @Schema(description = "恢复上线时间（NULL表示未恢复）")
    private LocalDateTime onlineTime;

    @Schema(description = "离线持续时长（秒）")
    private Long durationSeconds;

    @Schema(description = "触发原因")
    private String reason;

    @Schema(description = "处理人")
    private String handler;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    public static OfflineLogVO from(OfflineLog log) {
        OfflineLogVO vo = new OfflineLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}
