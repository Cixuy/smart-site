package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 喷淋操作日志表 (spray_operation_log)
 * 完整记录所有喷淋动作，满足环保监管审计要求。
 * 注：该表无 is_deleted 字段（操作日志不可逻辑删除）。
 */
@Data
@TableName("spray_operation_log")
public class SprayOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联位置ID */
    private Long locationId;

    /** 关联监测点ID（触发联动的环境传感器），手动/定时时为 null */
    private Long sensorId;

    /** 触发方式：auto-自动联动，manual-手动，schedule-定时 */
    private String triggerMode;

    /** 操作类型：start-启动，stop-关闭 */
    private String operationType;

    /** 操作原因（手动操作时建议必填） */
    private String reason;

    /** 操作人ID（自动联动时为 0 或 null） */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作时间 */
    private LocalDateTime createTime;
}
