package com.qst.smartbuildings.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EnvBatchPushResult {
    private int successCount;
    private int failCount;
    private boolean alarmTriggered;
}
