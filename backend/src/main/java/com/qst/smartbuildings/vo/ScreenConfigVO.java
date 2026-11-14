package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScreenConfigVO {
    private Long id;
    private String screenName;
    private Long projectId;
    private ScreenLayoutConfig layoutConfig;
    private Integer refreshInterval;
    private Boolean isDefault;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
