package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("screen_config")
public class ScreenConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String screenName;
    private Long projectId;
    private String layoutConfig;
    private Integer refreshInterval;
    private Integer isDefault;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
