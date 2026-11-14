package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScreenVideoRotationSaveRequest {
    @NotNull
    private Long cameraId;
    @Min(0)
    private Integer displayOrder = 0;
    @Min(1)
    private Integer duration = 10;
}
