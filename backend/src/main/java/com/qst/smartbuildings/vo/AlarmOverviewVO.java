package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AlarmOverviewVO {
    private long totalCount;
    private List<AlarmDimensionCountVO> statusDistribution;
    private List<AlarmDimensionCountVO> levelDistribution;
    private List<AlarmDimensionCountVO> sourceDistribution;
    private LocalDateTime generatedAt;
}