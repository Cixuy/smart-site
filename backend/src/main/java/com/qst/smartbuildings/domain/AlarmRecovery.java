package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_alarm_recovery")
public class AlarmRecovery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alarmId;
    private LocalDateTime recoveryTime;
    private BigDecimal recoveryValue;
    private String recoveryNote;
    private LocalDateTime createdAt;
}
