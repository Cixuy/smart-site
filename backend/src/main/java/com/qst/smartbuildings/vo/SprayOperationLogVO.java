package com.qst.smartbuildings.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 喷淋操作日志视图（与 SprayOperationLogMapper.selectPageVo 列严格对应）
 */
@Data
@Schema(description = "喷淋操作日志视图")
public class SprayOperationLogVO implements Serializable {
    private Long id;
    private Long locationId;
    private String locationName;
    private Long sensorId;
    private String triggerMode;
    private String operationType;
    private String reason;
    private Long operatorId;
    private String operatorName;
    @Schema(description = "操作时间（yyyy-MM-dd HH:mm:ss）")
    private String createTime;
}
