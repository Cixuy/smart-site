package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备告警项（详情卡片用）
 * <p>对应设计文档 3.2.3 alarms，数据源为告警管理模块 biz_alarm_record（尚未实现，暂返回空列表占位）。</p>
 */
@Data
@Schema(description = "设备告警项")
public class AlarmItemVO {

    @Schema(description = "告警ID")
    private Long alarmId;

    @Schema(description = "告警类型，如 力矩超标")
    private String alarmType;

    @Schema(description = "告警级别：warning/alarm/control")
    private String alarmLevel;

    @Schema(description = "告警时间")
    private String alarmTime;
}
