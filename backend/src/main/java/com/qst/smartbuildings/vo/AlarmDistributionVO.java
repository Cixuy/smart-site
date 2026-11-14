package com.qst.smartbuildings.vo;

import lombok.Data;

import java.util.List;

@Data
public class AlarmDistributionVO {
    private String dimension;
    private Long totalCount;
    private List<AlarmDistributionItemVO> items;
}