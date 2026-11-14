package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EnvDataManualRequest extends EnvDataReportRequest {
    @NotBlank
    private String operator;
    private String remark;
}
