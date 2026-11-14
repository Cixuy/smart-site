package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("screen_video_rotation")
public class ScreenVideoRotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long screenId;
    private Long cameraId;
    private Integer displayOrder;
    private Integer duration;
}
