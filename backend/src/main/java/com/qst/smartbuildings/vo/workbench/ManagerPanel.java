package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

import java.util.List;

/**
 * 项目经理/领导可见数据
 */
@Data
public class ManagerPanel {

    /** 近7日告警趋势 */
    private List<TrendPoint> alarmTrend;

    /** 设备在线率（%） */
    private Double onlineRate;

    /** 告警处置进度（%）：已处置告警数 / 全部告警数 */
    private Double disposalProgress;
}
