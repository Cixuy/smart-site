package com.qst.smartbuildings.vo;

import lombok.Data;

import java.util.List;

@Data
public class AlarmHistoryVO {
    private List<DisposalRecordVO> disposalRecords;
    private RecoveryRecordVO recoveryRecord;
}
