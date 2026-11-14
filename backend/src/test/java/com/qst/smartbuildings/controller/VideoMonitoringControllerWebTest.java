package com.qst.smartbuildings.controller;

import com.qst.smartbuildings.config.SecurityConfig;
import com.qst.smartbuildings.interceptor.AuthInterceptor;
import com.qst.smartbuildings.service.VideoMonitoringService;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.VideoCameraVO;
import com.qst.smartbuildings.vo.VideoRecordTimelineVO;
import com.qst.smartbuildings.vo.VideoRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoMonitoringController.class)
@Import(SecurityConfig.class)
class VideoMonitoringControllerWebTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private VideoMonitoringService videoMonitoringService;
    @MockBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void pageCamerasReturnsResultPage() throws Exception {
        VideoCameraVO camera = new VideoCameraVO();
        camera.setId(1L);
        camera.setCameraName("工地东门摄像头");
        camera.setName("工地东门摄像头");
        camera.setOnlineStatus(1);
        when(videoMonitoringService.pageCameras(null, null, null, null, 1, 20))
                .thenReturn(new PageResult<>(1, 1, 1, 20, List.of(camera)));

        mockMvc.perform(get("/video/cameras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].cameraName").value("工地东门摄像头"));
    }

    @Test
    void createCameraReturnsId() throws Exception {
        when(videoMonitoringService.createCamera(any())).thenReturn(51L);

        mockMvc.perform(post("/video/cameras")
                        .contentType("application/json")
                        .content("{\"cameraName\":\"新增摄像头\",\"locationId\":101,\"streamUrl\":\"rtsp://127.0.0.1:554/stream1\",\"protocolType\":\"RTSP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(51));
    }

    @Test
    void updateAndDeleteReturnSuccess() throws Exception {
        when(videoMonitoringService.updateCamera(eq(1L), any())).thenReturn(true);
        when(videoMonitoringService.deleteCamera(1L)).thenReturn(true);

        mockMvc.perform(put("/video/cameras/1")
                        .contentType("application/json")
                        .content("{\"cameraName\":\"摄像头-更新\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(delete("/video/cameras/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    void pageVideoRecordsReturnsRecords() throws Exception {
        VideoRecordVO record = new VideoRecordVO();
        record.setId(1L);
        record.setCameraId(1L);
        record.setCameraName("工地东门摄像头");
        record.setRecordType(1);
        record.setStartTime(LocalDateTime.of(2026, 8, 5, 14, 0));
        when(videoMonitoringService.pageVideoRecords(1L, null, null, null, 1, 20))
                .thenReturn(new PageResult<>(1, 1, 1, 20, List.of(record)));

        mockMvc.perform(get("/video/video-records").param("cameraId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].recordType").value(1));
    }

    @Test
    void timelineReturnsSegments() throws Exception {
        VideoRecordTimelineVO.VideoSegmentVO segment = new VideoRecordTimelineVO.VideoSegmentVO();
        segment.setStartTime("08:00:00");
        segment.setEndTime("08:30:00");
        VideoRecordTimelineVO timeline = new VideoRecordTimelineVO();
        timeline.setCameraId(1L);
        timeline.setDate("2026-08-05");
        timeline.setSegments(List.of(segment));
        when(videoMonitoringService.getTimeline(1L, "2026-08-05")).thenReturn(timeline);

        mockMvc.perform(get("/video/video-records/timeline")
                        .param("cameraId", "1")
                        .param("date", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.segments[0].startTime").value("08:00:00"));

        mockMvc.perform(get("/video/video-records/segments")
                        .param("cameraId", "1")
                        .param("date", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.segments[0].startTime").value("08:00:00"));
    }
}
