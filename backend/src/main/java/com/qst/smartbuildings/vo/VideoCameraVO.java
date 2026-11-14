package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoCameraVO {
    private Long id;
    private String cameraName;
    private String name;
    private String deviceId;
    private String deviceName;
    private Long locationId;
    private String locationName;
    private String location;
    private String streamUrl;
    private String protocolType;
    private Integer onlineStatus;
    private Integer status;
    private String ipAddress;
    private String onlineStatusText;
    private List<String> aiCapabilities;
    private LocalDateTime lastOnlineTime;
    private String remark;
}
