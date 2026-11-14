package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_camera")
public class BizCamera {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String cameraName;
    private String deviceId;
    private Long locationId;
    private String streamUrl;
    private String protocolType;
    private Integer onlineStatus;
    private String aiCapabilities;
    private LocalDateTime lastOnlineTime;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
