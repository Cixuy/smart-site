package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

import java.util.List;

/**
 * 告警摘要滚动列表数据
 */
@Data
public class AlarmSummaryListData {

    /** 告警记录列表 */
    private List<AlarmSummary> records;
}