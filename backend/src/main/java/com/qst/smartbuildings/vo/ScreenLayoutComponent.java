package com.qst.smartbuildings.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ScreenLayoutComponent {
    @NotBlank
    private String id;
    @NotBlank
    private String type;
    @Min(0)
    private Integer x = 0;
    @Min(0)
    private Integer y = 0;
    @Min(1)
    private Integer w = 1;
    @Min(1)
    private Integer h = 1;
    private Map<String, Object> props = new LinkedHashMap<>();
}
