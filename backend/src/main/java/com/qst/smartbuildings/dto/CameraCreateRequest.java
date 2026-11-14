package com.qst.smartbuildings.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class CameraCreateRequest {

    @JsonAlias("name")
    private String cameraName;

    private String deviceId;

    private Long locationId;

    private String location;

    private String streamUrl;

    private String protocolType;

    @JsonAlias("status")
    private Integer onlineStatus;

    private List<String> aiCapabilities;

    private String remark;
}
