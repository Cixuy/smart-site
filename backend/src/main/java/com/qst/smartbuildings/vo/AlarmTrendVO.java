package com.qst.smartbuildings.vo;

import lombok.Data;

import java.util.List;

@Data
public class AlarmTrendVO {
    private String granularity;
    private List<AlarmTrendPointVO> points;
}