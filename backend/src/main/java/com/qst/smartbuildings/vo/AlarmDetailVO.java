package com.qst.smartbuildings.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AlarmDetailVO extends AlarmListVO {
    private List<String> screenshotUrls;
    private List<DisposalRecordVO> disposalRecords;
    private RecoveryRecordVO recoveryRecord;
    private List<AlarmListVO> relatedAlarms;
    private Integer latestPushStatus;
}