package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

import java.util.List;

/**
 * 安全管理员可见数据
 */
@Data
public class SafetyAdminPanel {

    /** 待办告警列表（待处置 + 处置中） */
    private List<TodoAlarm> todoAlarms;

    /** 今日待巡检设备数 */
    private Integer patrolTaskCount;

    /** 今日喷淋操作记录数 */
    private Integer sprayRecordCount;
}
