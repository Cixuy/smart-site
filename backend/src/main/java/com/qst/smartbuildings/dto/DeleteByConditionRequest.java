package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Schema(name = "DeleteByConditionRequest", description = "按条件批量删除录像/截图请求体")
public class DeleteByConditionRequest {
    @NotNull(message = "cameraId 不能为空")
    @Schema(description = "摄像头ID", example = "1")
    private Long cameraId;

    @NotNull(message = "recordType 不能为空")
    @Schema(description = "记录类型：1-录像，2-截图，3-AI告警截图", example = "1")
    private Integer recordType;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间（含），格式 yyyy-MM-dd HH:mm:ss", example = "2026-08-01 00:00:00")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间（含），格式 yyyy-MM-dd HH:mm:ss", example = "2026-08-05 23:59:59")
    private LocalDateTime endTime;

    @Schema(description = "是否同时删除物理文件，默认 true", example = "true")
    private Boolean deleteFile = true;

    public boolean deleteFileOrDefault() {
        return deleteFile == null || deleteFile;
    }
}
