package com.qst.smartbuildings.service;

import com.qst.smartbuildings.dto.CameraCreateRequest;
import com.qst.smartbuildings.dto.CameraUpdateRequest;
import com.qst.smartbuildings.dto.DeleteAiSnapshotsRequest;
import com.qst.smartbuildings.dto.DeleteByConditionRequest;
import com.qst.smartbuildings.dto.DeleteTimelineRequest;
import com.qst.smartbuildings.dto.DeleteVideoRecordsRequest;
import com.qst.smartbuildings.vo.DeleteResponseData;
import com.qst.smartbuildings.vo.DeleteTimelineResponseData;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.VideoCameraVO;
import com.qst.smartbuildings.vo.VideoRecordTimelineVO;
import com.qst.smartbuildings.vo.VideoRecordVO;

public interface VideoMonitoringService {
    PageResult<VideoRecordVO> pageVideoRecords(Long cameraId, Integer recordType, String startTime, String endTime, long pageNum, long pageSize);

    PageResult<VideoRecordVO> pageAiSnapshots(Long cameraId, String startTime, String endTime, long pageNum, long pageSize);

    VideoRecordTimelineVO getTimeline(Long cameraId, String date);

    /** 按 ID 列表批量删除录像/截图记录（软删除，可选清理物理文件）。 */
    DeleteResponseData deleteVideoRecords(DeleteVideoRecordsRequest request);

    /** 删除单条录像/截图记录（软删除，可选清理物理文件）。 */
    VideoRecordVO deleteVideoRecordById(Long id, boolean deleteFile);

    /** 批量删除 AI 告警截图（支持按 ID 列表或按条件删除）。 */
    DeleteResponseData deleteAiSnapshots(DeleteAiSnapshotsRequest request);

    /** 删除单条 AI 告警截图（软删除，可选清理物理文件）。 */
    VideoRecordVO deleteAiSnapshotById(Long id, boolean deleteFile);

    /** 删除指定摄像头某天全部录像片段（高危，需二次确认）。 */
    DeleteTimelineResponseData deleteTimeline(DeleteTimelineRequest request);

    /** 按摄像头+记录类型+时间范围条件批量删除。 */
    DeleteResponseData deleteByCondition(DeleteByConditionRequest request);

    PageResult<VideoCameraVO> pageCameras(Long locationId, Integer onlineStatus, String deviceId, String cameraName, long pageNum, long pageSize);

    Long createCamera(CameraCreateRequest request);

    boolean updateCamera(Long id, CameraUpdateRequest request);

    boolean deleteCamera(Long id);

    VideoCameraVO getStreamUrl(Long id);
}
