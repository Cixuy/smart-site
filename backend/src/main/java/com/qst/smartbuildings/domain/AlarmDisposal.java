package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_alarm_disposal")
public class AlarmDisposal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alarmId;
    private Long operatorId;
    private String operatorName;
    private String disposalContent;
    private LocalDateTime disposalTime;
    private Integer disposalConclusion;
    private String falseReason;
}
