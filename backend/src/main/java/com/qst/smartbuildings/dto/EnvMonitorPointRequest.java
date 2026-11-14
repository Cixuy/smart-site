package com.qst.smartbuildings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EnvMonitorPointRequest {
    @NotBlank
    @Size(max = 30)
    private String name;
    @Size(max = 50)
    private String location;
}
