package com.qst.smartbuildings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_video_record")
public class BizVideoRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cameraId;
    private Integer recordType;
    private String filePath;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long fileSize;
    private LocalDateTime expireTime;
    private String triggerSource;
    /** AI 识别标签（仅 recordType=3 时），如：未佩戴安全帽 */
    private String aiLabel;
    /** AI 识别置信度（仅 recordType=3 时） */
    private Double aiConfidence;
    /** 软删除标记：0-正常，1-已删除 */
    private Integer deleted;
    private LocalDateTime createdAt;
}
