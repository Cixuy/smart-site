package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警摘要信息
 */
@Data
public class AlarmSummary {

    /** 告警ID */
    private Long alarmId;

    /** 告警类型描述 */
    private String alarmType;

    /** 告警级别：warning预警，alarm报警，control控制 */
    private String alarmLevel;

    /** 设备ID（AI告警可能为空） */
    private Long deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 告警时间 */
    private LocalDateTime alarmTime;
}
