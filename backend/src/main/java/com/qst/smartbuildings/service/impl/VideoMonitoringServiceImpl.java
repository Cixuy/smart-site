package com.qst.smartbuildings.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qst.smartbuildings.domain.AssetDeviceLocation;
import com.qst.smartbuildings.domain.AssetDeviceType;
import com.qst.smartbuildings.domain.BizCamera;
import com.qst.smartbuildings.domain.BizVideoRecord;
import com.qst.smartbuildings.domain.Device;
import com.qst.smartbuildings.dto.CameraCreateRequest;
import com.qst.smartbuildings.dto.CameraUpdateRequest;
import com.qst.smartbuildings.dto.DeleteAiSnapshotsRequest;
import com.qst.smartbuildings.dto.DeleteByConditionRequest;
import com.qst.smartbuildings.dto.DeleteTimelineRequest;
import com.qst.smartbuildings.dto.DeleteVideoRecordsRequest;
import com.qst.smartbuildings.exception.CameraException;
import com.qst.smartbuildings.mapper.AssetDeviceLocationMapper;
import com.qst.smartbuildings.mapper.AssetDeviceMapper;
import com.qst.smartbuildings.mapper.AssetDeviceTypeMapper;
import com.qst.smartbuildings.mapper.BizCameraMapper;
import com.qst.smartbuildings.mapper.BizVideoRecordMapper;
import com.qst.smartbuildings.service.VideoMonitoringService;
import com.qst.smartbuildings.util.AiFileUtil;
import com.qst.smartbuildings.vo.DeleteResponseData;
import com.qst.smartbuildings.vo.DeleteTimelineResponseData;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.VideoCameraVO;
import com.qst.smartbuildings.vo.VideoRecordTimelineVO;
import com.qst.smartbuildings.vo.VideoRecordVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VideoMonitoringServiceImpl implements VideoMonitoringService {

    private final BizVideoRecordMapper videoRecordMapper;
    private final BizCameraMapper bizCameraMapper;
    private final AssetDeviceLocationMapper assetDeviceLocationMapper;
    private final AssetDeviceMapper assetDeviceMapper;
    private final AssetDeviceTypeMapper assetDeviceTypeMapper;

    /** AI 截图存储根目录（与 WebConfig 的 /ai_image/** 静态映射一致） */
    @Value("${app.ai-image-dir:}")
    private String aiImageDir;

    public VideoMonitoringServiceImpl(BizVideoRecordMapper videoRecordMapper,
                                      BizCameraMapper bizCameraMapper,
                                      AssetDeviceLocationMapper assetDeviceLocationMapper,
                                      AssetDeviceMapper assetDeviceMapper,
                                      AssetDeviceTypeMapper assetDeviceTypeMapper) {
        this.videoRecordMapper = videoRecordMapper;
        this.bizCameraMapper = bizCameraMapper;
        this.assetDeviceLocationMapper = assetDeviceLocationMapper;
        this.assetDeviceMapper = assetDeviceMapper;
        this.assetDeviceTypeMapper = assetDeviceTypeMapper;
    }

    @PostConstruct
    public void init() {
        // No-op: cameras are stored in biz_camera.
    }

    @Override
    public PageResult<VideoRecordVO> pageVideoRecords(Long cameraId, Integer recordType, String startTime, String endTime, long pageNum, long pageSize) {
        LocalDateTime begin = parseDateTimeOptional(startTime);
        LocalDateTime end = parseDateTimeOptional(endTime);
        return pageRecords(cameraId, recordType, begin, end, pageNum, pageSize);
    }

    @Override
    public PageResult<VideoRecordVO> pageAiSnapshots(Long cameraId, String startTime, String endTime, long pageNum, long pageSize) {
        LocalDateTime begin = parseDateTimeOptional(startTime);
        LocalDateTime end = parseDateTimeOptional(endTime);
        return pageRecords(cameraId, 3, begin, end, pageNum, pageSize);
    }

    @Override
    public VideoRecordTimelineVO getTimeline(Long cameraId, String date) {
        if (cameraId == null) {
            throw new CameraException(400, "cameraId 不能为空");
        }
        LocalDate day = parseDate(date);
        String cameraName = resolveCameraName(cameraId);

        LocalDateTime begin = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        List<VideoRecordTimelineVO.VideoSegmentVO> segments = videoRecordMapper.selectList(new LambdaQueryWrapper<BizVideoRecord>()
                        .eq(BizVideoRecord::getCameraId, cameraId)
                        .eq(BizVideoRecord::getRecordType, 1)
                        .eq(BizVideoRecord::getDeleted, 0)
                        .ge(BizVideoRecord::getStartTime, begin)
                        .lt(BizVideoRecord::getStartTime, end)
                        .orderByAsc(BizVideoRecord::getStartTime))
                .stream()
                .map(record -> {
                    VideoRecordTimelineVO.VideoSegmentVO segment = new VideoRecordTimelineVO.VideoSegmentVO();
                    segment.setStartTime(record.getStartTime().toLocalTime().toString());
                    segment.setEndTime(record.getEndTime() == null ? null : record.getEndTime().toLocalTime().toString());
                    segment.setFilePath(record.getFilePath());
                    segment.setFileSize(record.getFileSize());
                    return segment;
                })
                .toList();

        VideoRecordTimelineVO vo = new VideoRecordTimelineVO();
        vo.setCameraId(cameraId);
        vo.setCameraName(cameraName);
        vo.setDate(day.toString());
        vo.setSegments(segments);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeleteResponseData deleteVideoRecords(DeleteVideoRecordsRequest request) {
        List<Long> ids = request.getIds() == null ? List.of() : request.getIds();
        if (ids.isEmpty() || ids.size() > 200) {
            throw new CameraException(40001, "ids 列表不能为空且最多200条");
        }
        return deleteByIds(ids, request.deleteFileOrDefault(), 40002);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoRecordVO deleteVideoRecordById(Long id, boolean deleteFile) {
        if (id == null) {
            throw new CameraException(40001, "记录ID不能为空");
        }
        BizVideoRecord record = videoRecordMapper.selectById(id);
        if (record == null || Objects.equals(record.getDeleted(), 1)) {
            throw new CameraException(40002, "记录不存在或已被删除");
        }
        markDeleted(record);
        if (deleteFile) {
            AiFileUtil.deleteLocalFile(record.getFilePath(), aiImageDir);
        }
        return toRecordVo(record, resolveCameraName(record.getCameraId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeleteResponseData deleteAiSnapshots(DeleteAiSnapshotsRequest request) {
        boolean hasIds = request.getIds() != null && !request.getIds().isEmpty();
        boolean hasCondition = request.getCameraId() != null
                || request.getStartTime() != null
                || request.getEndTime() != null
                || StringUtils.hasText(request.getAiLabel());
        if (!hasIds && !hasCondition) {
            throw new CameraException(40001, "请提供 ids 列表或 cameraId 条件");
        }

        List<Long> ids;
        if (hasIds) {
            ids = request.getIds();
            if (ids.size() > 200) {
                throw new CameraException(40001, "每次最多删除200条");
            }
        } else {
            LambdaQueryWrapper<BizVideoRecord> query = new LambdaQueryWrapper<BizVideoRecord>()
                    .eq(BizVideoRecord::getRecordType, 3)
                    .eq(BizVideoRecord::getDeleted, 0)
                    .eq(request.getCameraId() != null, BizVideoRecord::getCameraId, request.getCameraId())
                    .ge(request.getStartTime() != null, BizVideoRecord::getStartTime, request.getStartTime())
                    .le(request.getEndTime() != null, BizVideoRecord::getStartTime, request.getEndTime())
                    .like(StringUtils.hasText(request.getAiLabel()), BizVideoRecord::getAiLabel, request.getAiLabel())
                    .orderByAsc(BizVideoRecord::getId);
            List<BizVideoRecord> records = videoRecordMapper.selectList(query);
            ids = records.stream().map(BizVideoRecord::getId).toList();
            if (ids.isEmpty()) {
                return new DeleteResponseData(0, 0, 0, List.of());
            }
        }

        DeleteResponseData result = deleteByIds(ids, request.deleteFileOrDefault(), 40002);
        if (StringUtils.hasText(request.getAiLabel())) {
            result.setAiLabelFilter(request.getAiLabel());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoRecordVO deleteAiSnapshotById(Long id, boolean deleteFile) {
        if (id == null) {
            throw new CameraException(40001, "记录ID不能为空");
        }
        BizVideoRecord record = videoRecordMapper.selectById(id);
        if (record == null || Objects.equals(record.getDeleted(), 1)) {
            throw new CameraException(40002, "记录不存在或已被删除");
        }
        markDeleted(record);
        if (deleteFile) {
            AiFileUtil.deleteLocalFile(record.getFilePath(), aiImageDir);
        }
        return toRecordVo(record, resolveCameraName(record.getCameraId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeleteTimelineResponseData deleteTimeline(DeleteTimelineRequest request) {
        if (request.getCameraId() == null) {
            throw new CameraException(40001, "cameraId 不能为空");
        }
        if (request.getDate() == null) {
            throw new CameraException(40001, "date 不能为空");
        }
        if (!request.confirmed()) {
            throw new CameraException(40001, "请确认删除操作，confirm 必须为 true");
        }
        LocalDateTime begin = request.getDate().atStartOfDay();
        LocalDateTime end = request.getDate().plusDays(1).atStartOfDay();

        List<BizVideoRecord> records = videoRecordMapper.selectList(new LambdaQueryWrapper<BizVideoRecord>()
                .eq(BizVideoRecord::getCameraId, request.getCameraId())
                .eq(BizVideoRecord::getRecordType, 1)
                .eq(BizVideoRecord::getDeleted, 0)
                .ge(BizVideoRecord::getStartTime, begin)
                .lt(BizVideoRecord::getStartTime, end));

        long deletedSize = 0;
        for (BizVideoRecord record : records) {
            markDeleted(record);
            if (request.deleteFileOrDefault()) {
                AiFileUtil.deleteLocalFile(record.getFilePath(), aiImageDir);
            }
            if (record.getFileSize() != null) {
                deletedSize += record.getFileSize();
            }
        }

        DeleteTimelineResponseData data = new DeleteTimelineResponseData();
        data.setCameraId(request.getCameraId());
        data.setDate(request.getDate().toString());
        data.setDeletedSegments(records.size());
        data.setDeletedFileSize(deletedSize);
        data.setDeletedFileSizeText(humanSize(deletedSize));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeleteResponseData deleteByCondition(DeleteByConditionRequest request) {
        if (request.getCameraId() == null) {
            throw new CameraException(40001, "cameraId 不能为空");
        }
        if (request.getRecordType() == null || request.getRecordType() < 1 || request.getRecordType() > 3) {
            throw new CameraException(40001, "recordType 必须为 1(录像)、2(截图) 或 3(AI告警截图)");
        }
        if (request.getStartTime() != null && request.getEndTime() != null) {
            long days = ChronoUnit.DAYS.between(request.getStartTime(), request.getEndTime());
            if (days > 31) {
                throw new CameraException(40001, "时间范围不能超过31天");
            }
        }

        LambdaQueryWrapper<BizVideoRecord> query = new LambdaQueryWrapper<BizVideoRecord>()
                .eq(BizVideoRecord::getCameraId, request.getCameraId())
                .eq(BizVideoRecord::getRecordType, request.getRecordType())
                .eq(BizVideoRecord::getDeleted, 0)
                .ge(request.getStartTime() != null, BizVideoRecord::getStartTime, request.getStartTime())
                .le(request.getEndTime() != null, BizVideoRecord::getStartTime, request.getEndTime())
                .orderByAsc(BizVideoRecord::getId)
                .last("LIMIT 1000");

        List<BizVideoRecord> records = videoRecordMapper.selectList(query);
        List<Long> ids = records.stream().map(BizVideoRecord::getId).toList();
        DeleteResponseData result = deleteByIds(ids, request.deleteFileOrDefault(), 40002);

        String cameraName = resolveCameraName(request.getCameraId());
        if (cameraName != null) {
            result.setCameraName(cameraName);
        }
        result.setRecordTypeText(recordTypeText(request.getRecordType()));
        if (request.getStartTime() != null && request.getEndTime() != null) {
            result.setDateRange(request.getStartTime() + " ~ " + request.getEndTime());
        }
        return result;
    }

    /** 按 ID 列表软删除记录，返回统计结果；找不到的记录计入失败列表。 */
    private DeleteResponseData deleteByIds(List<Long> ids, boolean deleteFile, int notFoundCode) {
        int success = 0;
        Set<Long> failedIds = new LinkedHashSet<>();
        for (Long id : ids) {
            BizVideoRecord record = videoRecordMapper.selectById(id);
            if (record == null || Objects.equals(record.getDeleted(), 1)) {
                failedIds.add(id);
                continue;
            }
            try {
                markDeleted(record);
                if (deleteFile) {
                    AiFileUtil.deleteLocalFile(record.getFilePath(), aiImageDir);
                }
                success++;
            } catch (Exception e) {
                failedIds.add(id);
            }
        }
        return new DeleteResponseData(success, failedIds.size(), ids.size(), new ArrayList<>(failedIds));
    }

    private void markDeleted(BizVideoRecord record) {
        BizVideoRecord update = new BizVideoRecord();
        update.setId(record.getId());
        update.setDeleted(1);
        if (videoRecordMapper.updateById(update) <= 0) {
            throw new CameraException(40002, "记录不存在或已被删除");
        }
    }

    @Override
    public PageResult<VideoCameraVO> pageCameras(Long locationId, Integer onlineStatus, String deviceId, String cameraName, long pageNum, long pageSize) {
        long current = Math.max(1, pageNum);
        long size = Math.max(1, pageSize);
        LambdaQueryWrapper<BizCamera> query = new LambdaQueryWrapper<BizCamera>()
                .eq(BizCamera::getIsDeleted, 0)
                .eq(locationId != null, BizCamera::getLocationId, locationId)
                .eq(onlineStatus != null, BizCamera::getOnlineStatus, onlineStatus)
                .like(StringUtils.hasText(deviceId), BizCamera::getDeviceId, deviceId)
                .like(StringUtils.hasText(cameraName), BizCamera::getCameraName, cameraName)
                .orderByDesc(BizCamera::getId);
        IPage<BizCamera> page = bizCameraMapper.selectPage(new Page<>(current, size), query);
        List<VideoCameraVO> records = page.getRecords().stream()
                .map(this::toCameraVo)
                .collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), page.getPages(), current, size, records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCamera(CameraCreateRequest request) {
        if (!StringUtils.hasText(request.getCameraName())) {
            throw new CameraException(400, "cameraName 不能为空");
        }
        if (!StringUtils.hasText(request.getDeviceId())) {
            throw new CameraException(400, "deviceId 不能为空");
        }
        if (request.getLocationId() == null) {
            throw new CameraException(400, "locationId 不能为空");
        }
        if (!StringUtils.hasText(request.getStreamUrl())) {
            throw new CameraException(400, "streamUrl 不能为空");
        }
        if (!StringUtils.hasText(request.getProtocolType())) {
            throw new CameraException(400, "protocolType 不能为空");
        }
        requireLocation(request.getLocationId());
        String deviceCode = request.getDeviceId().trim();
        requireUniqueDeviceCode(deviceCode);
        createCameraDevice(request, deviceCode);

        BizCamera camera = new BizCamera();
        camera.setCameraName(request.getCameraName());
        camera.setDeviceId(deviceCode);
        camera.setLocationId(request.getLocationId());
        camera.setStreamUrl(request.getStreamUrl());
        camera.setProtocolType(request.getProtocolType());
        camera.setOnlineStatus(request.getOnlineStatus() == null ? 0 : request.getOnlineStatus());
        camera.setAiCapabilities(toJson(request.getAiCapabilities()));
        camera.setLastOnlineTime(camera.getOnlineStatus() != null && camera.getOnlineStatus() == 1 ? LocalDateTime.now() : null);
        camera.setRemark(request.getRemark());
        camera.setIsDeleted(0);
        camera.setCreatedAt(LocalDateTime.now());
        camera.setUpdatedAt(LocalDateTime.now());
        bizCameraMapper.insert(camera);
        return camera.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCamera(Long id, CameraUpdateRequest request) {
        BizCamera existing = bizCameraMapper.selectById(id);
        if (existing == null || Objects.equals(existing.getIsDeleted(), 1)) {
            throw new CameraException(404, "鎽勫儚澶翠笉瀛樺湪");
        }
        BizCamera camera = new BizCamera();
        camera.setId(id);
        if (StringUtils.hasText(request.getCameraName())) {
            camera.setCameraName(request.getCameraName());
        }
        if (StringUtils.hasText(request.getStreamUrl())) {
            camera.setStreamUrl(request.getStreamUrl());
        }
        if (request.getOnlineStatus() != null) {
            camera.setOnlineStatus(request.getOnlineStatus());
            if (request.getOnlineStatus() == 1) {
                camera.setLastOnlineTime(LocalDateTime.now());
            }
        }
        if (request.getAiCapabilities() != null) {
            camera.setAiCapabilities(toJson(request.getAiCapabilities()));
        }
        if (request.getRemark() != null) {
            camera.setRemark(request.getRemark());
        }
        camera.setUpdatedAt(LocalDateTime.now());
        boolean updated = bizCameraMapper.updateById(camera) > 0;
        if (StringUtils.hasText(existing.getDeviceId())) {
            Device deviceUpdate = new Device();
            if (StringUtils.hasText(request.getCameraName())) {
                deviceUpdate.setDeviceName(request.getCameraName());
            }
            if (request.getOnlineStatus() != null) {
                deviceUpdate.setOnlineStatus(request.getOnlineStatus());
                if (request.getOnlineStatus() == 1) {
                    deviceUpdate.setLastOnlineTime(LocalDateTime.now());
                }
            }
            deviceUpdate.setUpdateTime(LocalDateTime.now());
            assetDeviceMapper.update(deviceUpdate, new LambdaQueryWrapper<Device>()
                    .eq(Device::getDeviceCode, existing.getDeviceId()));
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCamera(Long id) {
        BizCamera existing = bizCameraMapper.selectById(id);
        if (existing == null || Objects.equals(existing.getIsDeleted(), 1)) {
            throw new CameraException(404, "鎽勫儚澶翠笉瀛樺湪");
        }
        bizCameraMapper.update(null, new LambdaUpdateWrapper<BizCamera>()
                .eq(BizCamera::getId, id)
                .set(BizCamera::getIsDeleted, 1)
                .set(BizCamera::getUpdatedAt, LocalDateTime.now()));
        if (StringUtils.hasText(existing.getDeviceId())) {
            assetDeviceMapper.delete(new LambdaQueryWrapper<Device>()
                    .eq(Device::getDeviceCode, existing.getDeviceId()));
        }
        return true;
    }

    @Override
    public VideoCameraVO getStreamUrl(Long id) {
        return requireCamera(id);
    }

    private PageResult<VideoRecordVO> pageRecords(Long cameraId, Integer recordType, LocalDateTime begin, LocalDateTime end,
                                                  long pageNum, long pageSize) {
        long current = Math.max(1, pageNum);
        long size = Math.max(1, pageSize);
        LambdaQueryWrapper<BizVideoRecord> query = new LambdaQueryWrapper<BizVideoRecord>()
                .eq(cameraId != null, BizVideoRecord::getCameraId, cameraId)
                .eq(recordType != null, BizVideoRecord::getRecordType, recordType)
                .eq(BizVideoRecord::getDeleted, 0)
                .ge(begin != null, BizVideoRecord::getStartTime, begin)
                .le(end != null, BizVideoRecord::getStartTime, end)
                .orderByDesc(BizVideoRecord::getStartTime);
        IPage<BizVideoRecord> page = videoRecordMapper.selectPage(new Page<>(current, size), query);
        List<VideoRecordVO> records = toRecordVos(page.getRecords());
        return new PageResult<>(page.getTotal(), page.getPages(), current, size, records);
    }

    private <T> PageResult<T> toPage(List<T> records, long pageNum, long pageSize) {
        long total = records.size();
        long pages = pageSize <= 0 ? 0 : (long) Math.ceil((double) total / pageSize);
        int from = (int) Math.min(Math.max((pageNum - 1) * pageSize, 0), total);
        int to = (int) Math.min(from + pageSize, total);
        List<T> pageRecords = total == 0 ? List.of() : new ArrayList<>(records.subList(from, to));
        return new PageResult<>(total, pages, pageNum, pageSize, pageRecords);
    }

    private VideoCameraVO requireCamera(Long id) {
        BizCamera camera = bizCameraMapper.selectById(id);
        if (camera == null || Objects.equals(camera.getIsDeleted(), 1)) {
            throw new CameraException(404, "摄像头不存在");
        }
        return toCameraVo(camera);
    }

    private List<VideoRecordVO> toRecordVos(List<BizVideoRecord> records) {
        Map<Long, String> cameraNames = loadCameraNames(records);
        return records.stream()
                .map(record -> toRecordVo(record, cameraNames.get(record.getCameraId())))
                .collect(Collectors.toList());
    }

    private VideoRecordVO toRecordVo(BizVideoRecord record, String cameraName) {
        VideoRecordVO vo = new VideoRecordVO();
        vo.setId(record.getId());
        vo.setCameraId(record.getCameraId());
        vo.setCameraName(cameraName);
        vo.setRecordType(record.getRecordType());
        vo.setRecordTypeText(recordTypeText(record.getRecordType()));
        vo.setFilePath(record.getFilePath());
        vo.setStartTime(record.getStartTime());
        vo.setEndTime(record.getEndTime());
        vo.setFileSize(record.getFileSize());
        vo.setFileSizeText(humanSize(record.getFileSize()));
        vo.setExpireTime(record.getExpireTime());
        vo.setTriggerSource(record.getTriggerSource());
        vo.setTriggerSourceText(triggerSourceText(record.getTriggerSource()));
        vo.setAiLabel(record.getAiLabel());
        vo.setAiConfidence(record.getAiConfidence());
        return vo;
    }

    private Map<Long, String> loadCameraNames(List<BizVideoRecord> records) {
        Map<Long, String> result = new HashMap<>();
        List<Long> cameraIds = records.stream()
                .map(BizVideoRecord::getCameraId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!cameraIds.isEmpty()) {
            bizCameraMapper.selectBatchIds(cameraIds).forEach(camera -> result.put(camera.getId(), camera.getCameraName()));
        }
        return result;
    }

    private String resolveCameraName(Long cameraId) {
        BizCamera dbCamera = bizCameraMapper.selectById(cameraId);
        if (dbCamera != null && !Objects.equals(dbCamera.getIsDeleted(), 1)) {
            return dbCamera.getCameraName();
        }
        return null;
    }

    private String resolveDeviceName(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        Device device = assetDeviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceCode, deviceId)
                .eq(Device::getIsDeleted, 0));
        if (device != null) {
            return device.getDeviceName();
        }
        return switch (deviceId) {
            case "TC001" -> "1号塔吊";
            case "HJ001" -> "环境监测主机";
            default -> deviceId;
        };
    }

    private String resolveLocationName(Long locationId) {
        if (locationId == null) {
            return null;
        }
        return switch (locationId.intValue()) {
            case 101 -> "东门";
            case 102 -> "西门";
            case 103 -> "塔吊区";
            default -> "位置-" + locationId;
        };
    }

    private LocalDateTime parseDateTimeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            String normalized = value.contains("T") ? value : value.replace(' ', 'T');
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            throw new CameraException(400, "date 不能为空");
        }
        return LocalDate.parse(value);
    }

    private String recordTypeText(Integer recordType) {
        if (recordType == null) {
            return null;
        }
        return switch (recordType) {
            case 1 -> "录像";
            case 2 -> "截图";
            case 3 -> "AI告警截图";
            default -> "未知";
        };
    }

    private String triggerSourceText(String triggerSource) {
        return switch (triggerSource == null ? "" : triggerSource) {
            case "manual" -> "手动";
            case "schedule" -> "定时录制";
            case "ai" -> "AI告警";
            default -> triggerSource;
        };
    }

    private String humanSize(Long size) {
        if (size == null) {
            return null;
        }
        double mb = size / 1024.0 / 1024.0;
        return String.format(java.util.Locale.ROOT, "%.2f MB", mb);
    }

    private void requireLocation(Long locationId) {
        AssetDeviceLocation location = assetDeviceLocationMapper.selectOne(new LambdaQueryWrapper<AssetDeviceLocation>()
                .eq(AssetDeviceLocation::getId, locationId)
                .eq(AssetDeviceLocation::getIsDeleted, 0));
        if (location == null) {
            throw new CameraException(400, "locationId 不存在或已删除");
        }
    }

    private void requireUniqueDeviceCode(String deviceCode) {
        Long count = assetDeviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceCode, deviceCode));
        if (count != null && count > 0) {
            throw new CameraException(400, "deviceId 已存在");
        }
    }

    private void
    createCameraDevice(CameraCreateRequest request, String deviceCode) {
        AssetDeviceType cameraType = assetDeviceTypeMapper.selectOne(new LambdaQueryWrapper<AssetDeviceType>()
                .eq(AssetDeviceType::getTypeCode, "MONITOR")
                .eq(AssetDeviceType::getIsDeleted, 0));
        if (cameraType == null) {
            throw new CameraException(400, "监控设备类型不存在或已删除");
        }

        LocalDate today = LocalDate.now();
        Device device = new Device();
        device.setDeviceCode(deviceCode);
        device.setDeviceName(request.getCameraName());
        device.setTypeId(cameraType.getId());
        device.setLocationId(request.getLocationId());
        device.setBrand("视频监控");
        device.setModel(request.getProtocolType());
        device.setProductionDate(today);
        device.setInstallDate(today);
        device.setEnableDate(today);
        device.setOnlineStatus(request.getOnlineStatus() == null ? 0 : request.getOnlineStatus());
        device.setEnableStatus(1);
        device.setLastOnlineTime(Objects.equals(device.getOnlineStatus(), 1) ? LocalDateTime.now() : null);
        device.setIsDeleted(0);
        device.setCreateTime(LocalDateTime.now());
        device.setUpdateTime(LocalDateTime.now());
        assetDeviceMapper.insert(device);
    }

    private VideoCameraVO toCameraVo(BizCamera camera) {
        VideoCameraVO vo = new VideoCameraVO();
        vo.setId(camera.getId());
        vo.setCameraName(camera.getCameraName());
        vo.setName(camera.getCameraName());
        vo.setDeviceId(camera.getDeviceId());
        vo.setDeviceName(resolveDeviceName(camera.getDeviceId()));
        vo.setLocationId(camera.getLocationId());
        vo.setLocationName(resolveLocationName(camera.getLocationId()));
        vo.setLocation(vo.getLocationName());
        vo.setStreamUrl(camera.getStreamUrl());
        vo.setProtocolType(camera.getProtocolType());
        vo.setOnlineStatus(camera.getOnlineStatus());
        vo.setStatus(camera.getOnlineStatus());
        vo.setIpAddress(camera.getStreamUrl());
        vo.setOnlineStatusText(Objects.equals(camera.getOnlineStatus(), 1) ? "在线" : "离线");
        vo.setAiCapabilities(parseAiCapabilities(camera.getAiCapabilities()));
        vo.setLastOnlineTime(camera.getLastOnlineTime());
        vo.setRemark(camera.getRemark());
        return vo;
    }

    private String toJson(List<String> values) {
        return values == null ? null : JSON.toJSONString(values);
    }

    private List<String> parseAiCapabilities(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return JSON.parseArray(value, String.class);
        } catch (Exception ignored) {
            return List.of(value);
        }
    }
}
