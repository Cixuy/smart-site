package com.qst.smartbuildings.dto;

import com.qst.smartbuildings.vo.ScreenLayoutConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ScreenConfigSaveRequest {
    @NotBlank
    @Size(max = 64)
    private String screenName;
    @NotNull
    private Long projectId;
    @NotNull
    @Valid
    private ScreenLayoutConfig layoutConfig;
    @Min(1)
    @Max(86400)
    private Integer refreshInterval = 5;
    private Boolean isDefault = false;
    @Min(0)
    @Max(1)
    private Integer status = 1;
}
