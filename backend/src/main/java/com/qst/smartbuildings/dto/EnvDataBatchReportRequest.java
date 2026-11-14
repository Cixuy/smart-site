package com.qst.smartbuildings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EnvDataBatchReportRequest {
    @NotEmpty
    @Size(max = 500)
    @Valid
    private List<EnvDataReportRequest> records;
}
