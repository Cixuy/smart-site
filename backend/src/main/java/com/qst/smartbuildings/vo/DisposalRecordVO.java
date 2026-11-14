package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DisposalRecordVO {
    private Long id;
    private Long alarmId;
    private Long operatorId;
    private String operatorName;
    private String disposalContent;
    private Integer disposalConclusion;
    private String falseReason;
    private LocalDateTime disposalTime;
}