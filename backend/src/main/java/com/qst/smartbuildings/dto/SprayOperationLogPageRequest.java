package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 喷淋操作日志分页请求
 */
@Data
@Schema(description = "喷淋操作日志分页请求")
public class SprayOperationLogPageRequest implements Serializable {
    @Schema(description = "当前页码，默认1")
    private Integer pageNum = 1;
    @Schema(description = "每页条数，默认20")
    private Integer pageSize = 20;
    @Schema(description = "位置ID")
    private Long locationId;
    @Schema(description = "触发方式：auto/manual/schedule")
    private String triggerMode;
    @Schema(description = "操作类型：start/stop")
    private String operationType;
    @Schema(description = "操作人姓名（模糊）")
    private String operatorName;
    @Schema(description = "开始时间（yyyy-MM-dd HH:mm:ss）")
    private String beginTime;
    @Schema(description = "结束时间（yyyy-MM-dd HH:mm:ss）")
    private String endTime;
}
