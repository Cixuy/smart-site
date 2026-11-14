package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_alarm_daily_stat")
public class AlarmDailyStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private LocalDate statDate;
    private Integer alarmLevel;
    private Integer sourceType;
    private Integer totalCount;
    private Integer pendingCount;
    private Integer resolvedCount;
    private Integer falseAlarmCount;
    private BigDecimal avgDisposalMinutes;
    private BigDecimal maxDisposalMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
