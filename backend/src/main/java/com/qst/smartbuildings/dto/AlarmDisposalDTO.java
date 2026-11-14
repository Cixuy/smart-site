package com.qst.smartbuildings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "AlarmDisposalDTO", description = "告警处置请求")
public class AlarmDisposalDTO {
    @NotNull
    @Schema(description = "告警ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long alarmId;
    @NotBlank
    @Size(max = 2000)
    @Schema(description = "处置内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String disposalContent;
    @NotNull
    @Schema(description = "处置结论：1-属实，2-误报，3-其他", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer disposalConclusion;
    @Size(max = 500)
    @Schema(description = "误报原因，结论为2时必填")
    private String falseReason;
}
