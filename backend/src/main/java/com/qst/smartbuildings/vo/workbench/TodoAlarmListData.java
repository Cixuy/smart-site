package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

import java.util.List;

/**
 * 安全管理员待办告警列表数据
 */
@Data
public class TodoAlarmListData {

    /** 待办告警记录列表 */
    private List<TodoAlarm> records;
}