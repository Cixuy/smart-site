package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_alarm_push_log")
public class AlarmPushLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alarmId;
    private Integer pushChannel;
    private String pushTarget;
    private Integer pushStatus;
    private LocalDateTime pushTime;
    private String responseCode;
    private String responseMsg;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}