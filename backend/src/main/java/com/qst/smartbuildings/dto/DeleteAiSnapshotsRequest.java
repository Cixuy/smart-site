package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(name = "DeleteAiSnapshotsRequest", description = "批量删除AI告警截图请求体（ID列表与条件删除二选一）")
public class DeleteAiSnapshotsRequest {
    @Size(max = 200, message = "每次最多删除200条")
    @Schema(description = "AI告警截图记录ID列表（与条件删除二选一）", example = "[50,51,52]")
    private List<Long> ids;

    @Schema(description = "摄像头ID（按条件删除时使用）", example = "1")
    private Long cameraId;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", example = "2026-08-01 00:00:00")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss", example = "2026-08-05 23:59:59")
    private LocalDateTime endTime;

    @Schema(description = "按AI识别标签过滤（可选），如：未佩戴安全帽", example = "未佩戴安全帽")
    private String aiLabel;

    @Schema(description = "是否同时删除物理文件，默认 true", example = "true")
    private Boolean deleteFile = true;

    public boolean deleteFileOrDefault() {
        return deleteFile == null || deleteFile;
    }
}
