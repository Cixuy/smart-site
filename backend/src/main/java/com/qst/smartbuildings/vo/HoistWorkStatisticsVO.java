package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 升降机作业统计 VO
 */
@Data
public class HoistWorkStatisticsVO {
    /** 作业总次数 */
    private Long totalWorkCount;
    /** 作业总时长（小时） */
    private BigDecimal totalWorkHours;
    /** 平均载重（kN） */
    private BigDecimal avgLoad;
    /** 最大载重（kN） */
    private BigDecimal maxLoad;
    /** 作业期告警次数 */
    private Long alarmCount;
}