package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(name = "DeleteVideoRecordsRequest", description = "按 ID 列表删除录像/截图请求体")
public class DeleteVideoRecordsRequest {
    @NotEmpty(message = "ids 列表不能为空")
    @Size(max = 200, message = "每次最多删除200条")
    @Schema(description = "要删除的记录ID列表", example = "[1,2,3]")
    private List<Long> ids;

    @Schema(description = "是否同时删除本地/OSS 物理文件，默认 true", example = "true")
    private Boolean deleteFile = true;

    public boolean deleteFileOrDefault() {
        return deleteFile == null || deleteFile;
    }
}
