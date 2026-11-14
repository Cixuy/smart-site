package com.qst.smartbuildings.vo;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ScreenLayoutConfig {
    private Integer gridCols = 12;
    private Integer rowHeight = 80;
    @Valid
    private List<ScreenLayoutComponent> components = new ArrayList<>();
}
