package com.qst.smartbuildings.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoRecordTimelineVO {
    private Long cameraId;
    private String cameraName;
    private String date;
    private List<VideoSegmentVO> segments;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VideoSegmentVO {
        private String startTime;
        private String endTime;
        private String filePath;
        private Long fileSize;
    }
}
