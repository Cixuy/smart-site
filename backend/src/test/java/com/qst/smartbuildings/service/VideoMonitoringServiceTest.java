package com.qst.smartbuildings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.AssetDeviceLocation;
import com.qst.smartbuildings.domain.AssetDeviceType;
import com.qst.smartbuildings.domain.BizCamera;
import com.qst.smartbuildings.domain.BizVideoRecord;
import com.qst.smartbuildings.domain.Device;
import com.qst.smartbuildings.dto.CameraCreateRequest;
import com.qst.smartbuildings.exception.CameraException;
import com.qst.smartbuildings.mapper.AssetDeviceLocationMapper;
import com.qst.smartbuildings.mapper.AssetDeviceMapper;
import com.qst.smartbuildings.mapper.AssetDeviceTypeMapper;
import com.qst.smartbuildings.mapper.BizCameraMapper;
import com.qst.smartbuildings.mapper.BizVideoRecordMapper;
import com.qst.smartbuildings.service.impl.VideoMonitoringServiceImpl;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.VideoCameraVO;
import com.qst.smartbuildings.vo.VideoRecordTimelineVO;
import com.qst.smartbuildings.vo.VideoRecordVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoMonitoringServiceTest {

    private final BizVideoRecordMapper videoRecordMapper = mock(BizVideoRecordMapper.class);
    private final BizCameraMapper bizCameraMapper = mock(BizCameraMapper.class);
    private final AssetDeviceLocationMapper assetDeviceLocationMapper = mock(AssetDeviceLocationMapper.class);
    private final AssetDeviceMapper assetDeviceMapper = mock(AssetDeviceMapper.class);
    private final AssetDeviceTypeMapper assetDeviceTypeMapper = mock(AssetDeviceTypeMapper.class);
    private final VideoMonitoringServiceImpl service = new VideoMonitoringServiceImpl(
            videoRecordMapper, bizCameraMapper, assetDeviceLocationMapper, assetDeviceMapper, assetDeviceTypeMapper);

    @Test
    void pageCamerasReturnsDatabaseData() {
        BizCamera camera = new BizCamera();
        camera.setId(1L);
        camera.setCameraName("工地东门摄像头");
        camera.setDeviceId("TC001");
        camera.setLocationId(101L);
        camera.setStreamUrl("rtsp://127.0.0.1:554/stream1");
        camera.setProtocolType("RTSP");
        camera.setOnlineStatus(1);
        camera.setAiCapabilities("[\"安全帽检测\"]");

        Page<BizCamera> dbPage = new Page<>(1, 20);
        dbPage.setRecords(List.of(camera));
        dbPage.setTotal(1);
        when(bizCameraMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(dbPage);

        PageResult<VideoCameraVO> page = service.pageCameras(null, null, null, null, 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getName()).isEqualTo(page.getRecords().get(0).getCameraName());
        assertThat(page.getRecords().get(0).getAiCapabilities()).containsExactly("安全帽检测");
    }

    @Test
    void createCameraAcceptsFrontendAliases() {
        CameraCreateRequest request = new CameraCreateRequest();
        request.setCameraName("新增摄像头");
        request.setDeviceId("CAMERA-001");
        request.setLocationId(101L);
        request.setStreamUrl("rtsp://127.0.0.1:554/stream1");
        request.setProtocolType("RTSP");
        request.setOnlineStatus(1);
        request.setAiCapabilities(List.of("安全帽检测"));
        AssetDeviceLocation location = new AssetDeviceLocation();
        location.setId(101L);
        location.setIsDeleted(0);
        when(assetDeviceLocationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(location);
        when(assetDeviceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        AssetDeviceType type = new AssetDeviceType();
        type.setId(1L);
        type.setTypeCode("MONITOR");
        type.setIsDeleted(0);
        when(assetDeviceTypeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(type);
        when(assetDeviceMapper.insert(any(Device.class))).thenReturn(1);
        doAnswer(invocation -> {
            BizCamera camera = invocation.getArgument(0);
            camera.setId(2L);
            return 1;
        }).when(bizCameraMapper).insert(any(BizCamera.class));
        BizCamera saved = new BizCamera();
        saved.setId(2L);
        saved.setCameraName("新增摄像头");
        saved.setDeviceId("CAMERA-001");
        saved.setLocationId(101L);
        saved.setStreamUrl("rtsp://127.0.0.1:554/stream1");
        saved.setProtocolType("RTSP");
        saved.setOnlineStatus(1);
        saved.setAiCapabilities("[\"安全帽检测\"]");
        when(bizCameraMapper.selectById(2L)).thenReturn(saved);

        Long id = service.createCamera(request);
        VideoCameraVO camera = service.getStreamUrl(id);

        assertThat(camera.getCameraName()).isEqualTo("新增摄像头");
        assertThat(camera.getLocation()).isEqualTo("东门");
        assertThat(camera.getStatus()).isEqualTo(1);
    }

    @Test
    void timelineRejectsMissingCameraId() {
        service.init();

        assertThatThrownBy(() -> service.getTimeline(null, "2026-08-05"))
                .isInstanceOf(CameraException.class)
                .hasMessageContaining("cameraId 不能为空");
    }

    @Test
    void aiSnapshotsOnlyReturnAiRecords() {
        service.init();
        BizVideoRecord dbRecord = new BizVideoRecord();
        dbRecord.setId(1L);
        dbRecord.setCameraId(1L);
        dbRecord.setRecordType(3);
        Page<BizVideoRecord> dbPage = new Page<>(1, 20);
        dbPage.setRecords(List.of(dbRecord));
        dbPage.setTotal(1);
        BizCamera camera = new BizCamera();
        camera.setId(1L);
        camera.setCameraName("工地东门摄像头");
        when(videoRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(dbPage);
        when(bizCameraMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(camera));

        PageResult<VideoRecordVO> page = service.pageAiSnapshots(1L, null, null, 1, 20);

        assertThat(page.getRecords()).allSatisfy(record -> assertThat(record.getRecordType()).isEqualTo(3));
    }
}
