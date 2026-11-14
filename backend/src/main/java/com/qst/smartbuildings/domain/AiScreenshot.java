package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_ai_screenshot")
public class AiScreenshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alarmId;
    private String screenshotUrl;
    /** 截图类型：1-原始截图，2-标注截图 */
    private Integer screenshotType;
    /** 文件大小（Bytes） */
    private Long fileSize;
    /** 图片宽度（像素） */
    private Integer width;
    /** 图片高度（像素） */
    private Integer height;
    private LocalDateTime createdAt;
}
