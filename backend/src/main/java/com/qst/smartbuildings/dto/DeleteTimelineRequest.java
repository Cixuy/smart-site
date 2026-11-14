package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Schema(name = "DeleteTimelineRequest", description = "删除指定摄像头某天全部录像片段请求体（高危操作，需二次确认）")
public class DeleteTimelineRequest {
    @NotNull(message = "cameraId 不能为空")
    @Schema(description = "摄像头ID", example = "1")
    private Long cameraId;

    @NotNull(message = "date 不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "日期，格式 yyyy-MM-dd", example = "2026-08-05")
    private LocalDate date;

    @Schema(description = "二次确认标志，必须为 true 才执行", example = "true")
    private Boolean confirm = false;

    @Schema(description = "是否同时删除物理文件，默认 true", example = "true")
    private Boolean deleteFile = true;

    public boolean confirmed() {
        return confirm != null && confirm;
    }

    public boolean deleteFileOrDefault() {
        return deleteFile == null || deleteFile;
    }
}
