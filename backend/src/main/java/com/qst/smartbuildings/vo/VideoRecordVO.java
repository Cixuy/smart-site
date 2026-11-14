package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoRecordVO {
    private Long id;
    private Long cameraId;
    private String cameraName;
    private Integer recordType;
    private String recordTypeText;
    private String filePath;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long fileSize;
    private String fileSizeText;
    private LocalDateTime expireTime;
    private String triggerSource;
    private String triggerSourceText;
    private String aiLabel;
    private Double aiConfidence;
}
