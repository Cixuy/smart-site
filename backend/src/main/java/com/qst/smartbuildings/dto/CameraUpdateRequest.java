package com.qst.smartbuildings.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class CameraUpdateRequest {
    @JsonAlias("name")
    private String cameraName;
    private String streamUrl;
    private String location;
    @JsonAlias("status")
    private Integer onlineStatus;
    private List<String> aiCapabilities;
    private String remark;
}
