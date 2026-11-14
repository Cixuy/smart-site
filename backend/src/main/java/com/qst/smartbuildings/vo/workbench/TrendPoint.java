package com.qst.smartbuildings.vo.workbench;

import lombok.Data;

import java.time.LocalDate;

/**
 * 趋势图数据点
 */
@Data
public class TrendPoint {

    /** 日期 */
    private LocalDate date;

    /** 数值 */
    private Long value;
}
