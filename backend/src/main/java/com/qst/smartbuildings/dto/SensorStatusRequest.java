package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

@Data
public class SensorStatusRequest {
    @NotNull @Min(0) @Max(2) private Integer status;
}
