package com.qst.smartbuildings.vo;

import lombok.Data;

@Data
public class ScreenVideoRotationVO {
    private Long id;
    private Long screenId;
    private Long cameraId;
    private String cameraName;
    private String streamUrl;
    private Integer displayOrder;
    private Integer duration;
}
