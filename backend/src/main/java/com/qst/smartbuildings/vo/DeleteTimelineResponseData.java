package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeleteTimelineResponseData {
    private Long cameraId;
    private String date;
    private Integer deletedSegments;
    private Long deletedFileSize;
    private String deletedFileSizeText;
}
