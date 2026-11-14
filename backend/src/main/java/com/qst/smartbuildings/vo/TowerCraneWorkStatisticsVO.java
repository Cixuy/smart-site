package com.qst.smartbuildings.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 塔吊作业统计汇总 VO
 */
@Data
public class TowerCraneWorkStatisticsVO {
    /** 作业总次数 */
    private Long totalWorkCount;
    /** 作业总时长（小时） */
    private BigDecimal totalWorkHours;
    /** 平均吊重（kN） */
    private BigDecimal avgLoad;
    /** 最大吊重（kN） */
    private BigDecimal maxLoad;
    /** 作业期告警次数 */
    private Long alarmCount;
}