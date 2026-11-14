package com.qst.smartbuildings.vo.workbench;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 安全管理员待办告警
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TodoAlarm extends AlarmSummary {

    /** 处置状态：pending待处置，processing处置中 */
    private String status;

    /** 处置跳转链接 */
    private String disposalUrl;
}
