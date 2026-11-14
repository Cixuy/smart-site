package com.qst.smartbuildings.service;

import com.qst.smartbuildings.domain.AiScreenshot;
import com.qst.smartbuildings.domain.AlarmDisposal;
import com.qst.smartbuildings.domain.AlarmRecord;
import com.qst.smartbuildings.domain.BizVideoRecord;
import com.qst.smartbuildings.dto.AiScreenshotCreateDTO;
import com.qst.smartbuildings.dto.AlarmCreateDTO;
import com.qst.smartbuildings.dto.AlarmDisposalDTO;
import com.qst.smartbuildings.dto.AlarmRecoveryDTO;
import com.qst.smartbuildings.exception.AlarmException;
import com.qst.smartbuildings.exception.AlarmNotFoundException;
import com.qst.smartbuildings.mapper.AiScreenshotMapper;
import com.qst.smartbuildings.mapper.AlarmDailyStatMapper;
import com.qst.smartbuildings.mapper.AlarmDisposalMapper;
import com.qst.smartbuildings.mapper.AlarmPushLogMapper;
import com.qst.smartbuildings.mapper.AlarmRecoveryMapper;
import com.qst.smartbuildings.mapper.AlarmRecordMapper;
import com.qst.smartbuildings.mapper.BizVideoRecordMapper;
import com.qst.smartbuildings.vo.AlarmCountVO;
import com.qst.smartbuildings.vo.AlarmDetailVO;
import com.qst.smartbuildings.vo.AlarmDimensionCountVO;
import com.qst.smartbuildings.vo.AlarmDistributionItemVO;
import com.qst.smartbuildings.vo.AlarmDistributionVO;
import com.qst.smartbuildings.vo.AlarmHistoryVO;
import com.qst.smartbuildings.vo.AlarmListVO;
import com.qst.smartbuildings.vo.AlarmOverviewVO;
import com.qst.smartbuildings.vo.AlarmPushLogVO;
import com.qst.smartbuildings.vo.AlarmTimelinessVO;
import com.qst.smartbuildings.vo.AlarmTrendVO;
import com.qst.smartbuildings.vo.AiAlarmDetailVO;
import com.qst.smartbuildings.vo.DailyAlarmStatVO;
import com.qst.smartbuildings.vo.DisposalRecordVO;
import com.qst.smartbuildings.vo.PageResult;
import com.qst.smartbuildings.vo.RecoveryRecordVO;
import com.qst.smartbuildings.vo.OperatorTimelinessVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AlarmService {
    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    private final AlarmRecordMapper alarmRecordMapper;
    private final AlarmDisposalMapper disposalMapper;
    private final AlarmRecoveryMapper recoveryMapper;
    private final AlarmDailyStatMapper dailyStatMapper;
    private final AlarmPushLogMapper pushLogMapper;
    private final AiScreenshotMapper screenshotMapper;
    private final BizVideoRecordMapper videoRecordMapper;

    /** AI 截图存储根目录（与 WebConfig 的 /ai_image/** 静态映射一致，用于删除物理文件） */
    @Value("${app.ai-image-dir:}")
    private String aiImageDir;

    public AlarmService(AlarmRecordMapper alarmRecordMapper, AlarmDisposalMapper disposalMapper,
                        AlarmRecoveryMapper recoveryMapper, AlarmDailyStatMapper dailyStatMapper,
                        AiScreenshotMapper screenshotMapper, AlarmPushLogMapper pushLogMapper,
                        BizVideoRecordMapper videoRecordMapper) {
        this.alarmRecordMapper = alarmRecordMapper;
        this.disposalMapper = disposalMapper;
        this.recoveryMapper = recoveryMapper;
        this.dailyStatMapper = dailyStatMapper;
        this.pushLogMapper = pushLogMapper;
        this.screenshotMapper = screenshotMapper;
        this.videoRecordMapper = videoRecordMapper;
    }

    public PageResult<AlarmListVO> pageAi(long pageNum, long pageSize, LocalDateTime startTime,
                                          LocalDateTime endTime, Integer status, String alarmTypeCode) {
        validatePage(pageNum, pageSize, 500);
        validateTimeRange(startTime, endTime);
        validateDimension("处置状态", status, 0, 2);
        String type = StringUtils.hasText(alarmTypeCode) ? alarmTypeCode.trim() : null;
        long total = alarmRecordMapper.countAiPage(status, type, startTime, endTime);
        List<AlarmListVO> records = alarmRecordMapper.selectAiPage(status, type, startTime, endTime,
                (pageNum - 1) * pageSize, pageSize);
        return new PageResult<>(total, pages(total, pageSize), pageNum, pageSize, records);
    }

    public PageResult<AlarmListVO> page(long pageNum, long pageSize, Long projectId, Integer sourceType,
                                        Integer alarmLevel, Integer status, Long deviceId, LocalDateTime startTime,
                                        LocalDateTime endTime) {
        return page(pageNum, pageSize, projectId, sourceType, null, alarmLevel, status, null, deviceId,
                null, null, null, null, startTime, endTime);
    }

    public PageResult<AlarmListVO> page(long pageNum, long pageSize, Long projectId, Integer sourceType,
                                        Long deviceTypeId, Integer alarmLevel, Integer status, String alarmTypeCode,
                                        Long deviceId, Long monitorPointId, Long cameraId, Long relatedAlarmId,
                                        String keyword, LocalDateTime startTime, LocalDateTime endTime) {
        validatePage(pageNum, pageSize, 500);
        validateTimeRange(startTime, endTime);
        validatePositive("projectId", projectId);
        validatePositive("deviceTypeId", deviceTypeId);
        validateDimension("告警来源", sourceType, 1, 3);
        validateDimension("告警级别", alarmLevel, 1, 3);
        validateDimension("处置状态", status, 0, 2);
        String type = normalize(alarmTypeCode);
        String search = normalize(keyword);
        long total = alarmRecordMapper.countPage(projectId, sourceType, deviceTypeId, alarmLevel, status, type,
                deviceId, monitorPointId, cameraId, relatedAlarmId, search, startTime, endTime);
        List<AlarmListVO> records = alarmRecordMapper.selectPage(projectId, sourceType, deviceTypeId, alarmLevel,
                status, type, deviceId, monitorPointId, cameraId, relatedAlarmId, search, startTime, endTime,
                (pageNum - 1) * pageSize, pageSize);
        return new PageResult<>(total, pages(total, pageSize), pageNum, pageSize, records);
    }

    public PageResult<AlarmListVO> recordPage(long pageNum, long pageSize, Long projectId, Integer sourceType,
            Integer alarmLevel, String alarmTypeCode, Integer status, Long deviceId, Long cameraId,
            Long monitorPointId, String alarmNo, String alarmContent, Long relatedAlarmId, Integer isDeleted,
            LocalDateTime startTime, LocalDateTime endTime, String sortField, String sortOrder) {
        validatePage(pageNum, pageSize, 200);
        validateTimeRange(startTime, endTime);
        validateDimension("告警来源", sourceType, 1, 3);
        validateDimension("告警级别", alarmLevel, 1, 3);
        validateDimension("处置状态", status, 0, 2);
        validateDimension("逻辑删除标记", isDeleted, 0, 1);
        if (isDeleted != null && isDeleted == 1) {
            throw new AlarmException("已删除告警不可通过业务列表查询");
        }
        String type = normalize(alarmTypeCode);
        String no = normalize(alarmNo);
        String content = normalize(alarmContent);
        String orderField = switch (sortField == null ? "alarm_time" : sortField) {
            case "alarm_time", "id", "alarm_level", "status", "created_at" -> sortField == null ? "alarm_time" : sortField;
            default -> throw new AlarmException("sortField 参数不合法");
        };
        String order = StringUtils.hasText(sortOrder) ? sortOrder.toLowerCase() : "desc";
        if (!List.of("asc", "desc").contains(order)) throw new AlarmException("sortOrder 参数不合法");
        long total = alarmRecordMapper.countRecordPage(projectId, sourceType, alarmLevel, type, status, deviceId,
                cameraId, monitorPointId, no, content, relatedAlarmId, startTime, endTime);
        return new PageResult<>(total, pages(total, pageSize), pageNum, pageSize,
                alarmRecordMapper.selectRecordPage(projectId, sourceType, alarmLevel, type, status, deviceId,
                        cameraId, monitorPointId, no, content, relatedAlarmId, startTime, endTime,
                        orderField, order, (pageNum - 1) * pageSize, pageSize));
    }

    public AlarmOverviewVO overview(Long projectId, LocalDateTime startTime, LocalDateTime endTime) {
        validatePositive("projectId", projectId);
        validateTimeRange(startTime, endTime);
        List<AlarmCountVO> statuses = alarmRecordMapper.countByStatus(projectId, startTime, endTime);
        List<AlarmCountVO> levels = alarmRecordMapper.countByLevel(projectId, startTime, endTime);
        List<AlarmCountVO> sources = alarmRecordMapper.countBySource(projectId, startTime, endTime);
        AlarmOverviewVO result = new AlarmOverviewVO();
        result.setStatusDistribution(dimensions(statuses, Map.of(0, "未处置", 1, "处置中", 2, "已处置"), "status"));
        result.setLevelDistribution(dimensions(levels, Map.of(1, "预警", 2, "警报", 3, "控制"), "alarmLevel"));
        result.setSourceDistribution(dimensions(sources, Map.of(1, "设备监测告警", 2, "环境监测告警", 3, "AI识别告警"), "sourceType"));
        result.setTotalCount(statuses.stream().mapToLong(AlarmCountVO::getCount).sum());
        result.setGeneratedAt(LocalDateTime.now());
        return result;
    }

    public AlarmOverviewVO overview(Long projectId) {
        return overview(projectId, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(AlarmCreateDTO request) {
        if (request == null) throw new AlarmException("告警参数不能为空");
        validateDimension("告警来源", request.getSourceType(), 1, 3);
        validateDimension("告警级别", request.getAlarmLevel(), 1, 3);
        validatePositive("projectId", request.getProjectId());
        if (!StringUtils.hasText(request.getAlarmTypeCode()) || !StringUtils.hasText(request.getAlarmContent())) {
            throw new AlarmException("告警类型和内容不能为空");
        }
        AlarmRecord record = new AlarmRecord();
        String alarmTimePart = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(
                request.getAlarmTime() == null ? LocalDateTime.now() : request.getAlarmTime());
        String randomPart = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        record.setAlarmNo("AL" + alarmTimePart + randomPart);
        record.setProjectId(request.getProjectId());
        record.setSourceType(request.getSourceType());
        record.setAlarmLevel(request.getAlarmLevel());
        record.setAlarmTypeCode(request.getAlarmTypeCode().trim());
        record.setAlarmContent(request.getAlarmContent().trim());
        record.setCurrentValue(request.getCurrentValue());
        record.setThresholdRange(request.getThresholdRange());
        record.setDeviceId(request.getDeviceId());
        record.setMonitorPointId(request.getMonitorPointId());
        record.setCameraId(request.getCameraId());
        record.setRelatedAlarmId(request.getRelatedAlarmId());
        record.setAlarmTime(request.getAlarmTime() == null ? LocalDateTime.now() : request.getAlarmTime());
        record.setStatus(0);
        record.setIsDeleted(0);
        if (alarmRecordMapper.insert(record) != 1) throw new AlarmException("生成告警失败");
        rebuildDailyStatistics(record.getProjectId(), record.getAlarmTime().toLocalDate());
        return record.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id) {
        requirePositiveId(id);
        AlarmRecord alarm = requireAlarm(id);
        if (!Integer.valueOf(0).equals(alarm.getStatus())) throw new AlarmException("仅未处置告警可以接单");
        if (alarmRecordMapper.markHandling(id) != 1) throw new AlarmException("告警接单失败");
        rebuildDailyStatistics(alarm.getProjectId(), alarm.getAlarmTime().toLocalDate());
    }

    @Transactional(rollbackFor = Exception.class)
    public void recover(AlarmRecoveryDTO request) {
        if (request == null || request.getAlarmId() == null) throw new AlarmException("告警ID不能为空");
        AlarmRecord alarm = requireAlarm(request.getAlarmId());
        if (recoveryMapper.selectIdByAlarmId(alarm.getId()) != null) return;
        com.qst.smartbuildings.domain.AlarmRecovery recovery = new com.qst.smartbuildings.domain.AlarmRecovery();
        recovery.setAlarmId(alarm.getId());
        recovery.setRecoveryTime(request.getRecoveryTime() == null ? LocalDateTime.now() : request.getRecoveryTime());
        recovery.setRecoveryValue(request.getRecoveryValue());
        recovery.setRecoveryNote(StringUtils.hasText(request.getRecoveryNote()) ? request.getRecoveryNote().trim() : "监测值已恢复正常范围");
        if (recoveryMapper.insert(recovery) != 1) throw new AlarmException("保存恢复记录失败");
        rebuildDailyStatistics(alarm.getProjectId(), alarm.getAlarmTime() == null ? LocalDate.now() : alarm.getAlarmTime().toLocalDate());
    }

    /**
     * 设备监测告警（source_type=1，塔吊/升降机等传感器）：达到阈值时写 biz_alarm_record，
     * 恢复正常时自动恢复；同一监测点（传感器）仅保留一条未恢复告警，避免重复告警刷屏。
     */
    @Transactional(rollbackFor = Exception.class)
    public void processDeviceAlarm(Long projectId, Long deviceId, Long monitorPointId, String metricType,
                                   BigDecimal value, int status, LocalDateTime eventTime, String thresholdRange,
                                   String alarmContent) {
        if (projectId == null || projectId < 1 || monitorPointId == null) return;
        if (status > 0) {
            if (alarmRecordMapper.selectRecoverableByMonitorPoint(1, monitorPointId).isEmpty()) {
                AlarmCreateDTO request = new AlarmCreateDTO();
                request.setProjectId(projectId);
                request.setSourceType(1);
                request.setAlarmLevel(status);
                request.setAlarmTypeCode(metricType + "_EXCEED");
                request.setAlarmContent(alarmContent);
                request.setCurrentValue(value);
                request.setThresholdRange(thresholdRange);
                request.setDeviceId(deviceId);
                request.setMonitorPointId(monitorPointId);
                request.setMonitorPointParentType(1);
                request.setAlarmTime(eventTime);
                create(request);
            }
        } else {
            for (Long alarmId : alarmRecordMapper.selectRecoverableByMonitorPoint(1, monitorPointId)) {
                AlarmRecoveryDTO recovery = new AlarmRecoveryDTO();
                recovery.setAlarmId(alarmId);
                recovery.setRecoveryTime(eventTime);
                recovery.setRecoveryValue(value);
                recovery.setRecoveryNote(metricType + "监测值已恢复正常范围");
                recover(recovery);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processEnvironmentAlarm(Long projectId, Long deviceId, Long monitorPointId, String metricType,
                                        BigDecimal value, int status, LocalDateTime eventTime, String thresholdRange) {
        if (projectId == null || projectId < 1 || monitorPointId == null) return;
        if (status > 0) {
            if (alarmRecordMapper.selectRecoverableByMonitorPoint(2, monitorPointId).isEmpty()) {
                AlarmCreateDTO request = new AlarmCreateDTO();
                request.setProjectId(projectId);
                request.setSourceType(2);
                request.setAlarmLevel(status);
                request.setAlarmTypeCode(metricType + "_EXCEED");
                request.setAlarmContent(metricType + "监测值超出阈值");
                request.setCurrentValue(value);
                request.setThresholdRange(thresholdRange);
                request.setDeviceId(deviceId);
                request.setMonitorPointId(monitorPointId);
                request.setMonitorPointParentType(2);
                request.setAlarmTime(eventTime);
                create(request);
            }
        } else {
            for (Long alarmId : alarmRecordMapper.selectRecoverableByMonitorPoint(2, monitorPointId)) {
                AlarmRecoveryDTO recovery = new AlarmRecoveryDTO();
                recovery.setAlarmId(alarmId);
                recovery.setRecoveryTime(eventTime);
                recovery.setRecoveryValue(value);
                recovery.setRecoveryNote(metricType + "监测值已恢复正常范围");
                recover(recovery);
            }
        }
    }

    public AlarmDetailVO detail(Long id) {
        AlarmListVO base = requireDetail(id);
        AlarmDetailVO result = new AlarmDetailVO();
        BeanUtils.copyProperties(base, result);
        result.setScreenshotUrls(screenshotMapper.selectUrls(id));
        result.setDisposalRecords(disposalMapper.selectHistory(id));
        result.setRecoveryRecord(recoveryMapper.selectHistory(id));
        result.setRelatedAlarms(alarmRecordMapper.selectRelated(id));
        result.setLatestPushStatus(pushLogMapper.selectLatestStatus(id));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void logicalDelete(Long id) {
        requirePositiveId(id);
        AlarmListVO alarm = requireDetail(id);
        if (alarmRecordMapper.logicalDelete(id) != 1) throw new AlarmException("告警删除失败");
        rebuildDailyStatistics(alarm.getProjectId(), alarm.getAlarmTime().toLocalDate());
    }

    public PageResult<DisposalRecordVO> disposals(Long id, long pageNum, long pageSize) {
        requireDetail(id);
        validatePage(pageNum, pageSize, 100);
        long total = disposalMapper.countPage(id);
        return new PageResult<>(total, pages(total, pageSize), pageNum, pageSize,
                disposalMapper.selectPage(id, (pageNum - 1) * pageSize, pageSize));
    }

    public RecoveryRecordVO recovery(Long id) {
        requireDetail(id);
        RecoveryRecordVO recovery = recoveryMapper.selectHistory(id);
        if (recovery == null) throw new AlarmNotFoundException("告警恢复记录不存在");
        return recovery;
    }

    public List<AlarmListVO> related(Long id) {
        requireDetail(id);
        return alarmRecordMapper.selectRelated(id);
    }

    public AlarmTrendVO trend(Long projectId, LocalDate startDate, LocalDate endDate, String granularity,
                              Integer sourceType, Integer alarmLevel) {
        validatePositive("projectId", projectId);
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) throw new AlarmException("日期范围不合法");
        String g = StringUtils.hasText(granularity) ? granularity.toUpperCase() : "DAY";
        if (!List.of("DAY", "WEEK", "MONTH").contains(g)) throw new AlarmException("granularity 参数不合法");
        validateDimension("告警来源", sourceType, 1, 3);
        validateDimension("告警级别", alarmLevel, 1, 3);
        AlarmTrendVO result = new AlarmTrendVO();
        result.setGranularity(g);
        result.setPoints(alarmRecordMapper.selectTrend(projectId, startDate, endDate, g, sourceType, alarmLevel));
        return result;
    }

    public AlarmDistributionVO distribution(Long projectId, LocalDateTime startTime, LocalDateTime endTime,
                                            String dimension, Integer sourceType) {
        validatePositive("projectId", projectId);
        validateTimeRangeRequired(startTime, endTime);
        String d = StringUtils.hasText(dimension) ? dimension.toUpperCase() : "ALARM_TYPE";
        if (!List.of("ALARM_TYPE", "DEVICE_TYPE").contains(d)) throw new AlarmException("dimension 参数不合法");
        validateDimension("告警来源", sourceType, 1, 3);
        List<AlarmDistributionItemVO> items = "DEVICE_TYPE".equals(d)
                ? alarmRecordMapper.selectDeviceTypeDistribution(projectId, startTime, endTime, sourceType)
                : alarmRecordMapper.selectAlarmTypeDistribution(projectId, startTime, endTime, sourceType);
        long total = items.stream().mapToLong(AlarmDistributionItemVO::getCount).sum();
        items.forEach(item -> item.setPercentage(percent(item.getCount(), total)));
        AlarmDistributionVO result = new AlarmDistributionVO();
        result.setDimension(d);
        result.setTotalCount(total);
        result.setItems(items);
        return result;
    }

    public AlarmTimelinessVO timeliness(Long projectId, LocalDateTime startTime, LocalDateTime endTime,
                                        Integer deadlineMinutes, Long operatorId) {
        validatePositive("projectId", projectId);
        validateTimeRangeRequired(startTime, endTime);
        int deadline = deadlineMinutes == null ? 30 : deadlineMinutes;
        if (deadline < 1 || deadline > 10080) throw new AlarmException("deadlineMinutes 参数不合法");
        validatePositive("operatorId", operatorId);
        List<OperatorTimelinessVO> operators = disposalMapper.selectTimeliness(projectId, startTime, endTime,
                deadline * 60L, operatorId);
        int disposed = operators.stream().mapToInt(v -> value(v.getDisposedCount())).sum();
        int timely = operators.stream().mapToInt(v -> value(v.getTimelyCount())).sum();
        int overdue = operators.stream().mapToInt(v -> value(v.getOverdueCount())).sum();
        operators.forEach(v -> v.setTimelyRate(percent(value(v.getTimelyCount()), value(v.getDisposedCount()))));
        AlarmTimelinessVO result = new AlarmTimelinessVO();
        result.setDeadlineMinutes(deadline);
        result.setDisposedCount(disposed);
        result.setTimelyCount(timely);
        result.setOverdueCount(overdue);
        result.setTimelyRate(percent(timely, disposed));
        result.setOperators(operators);
        return result;
    }

    public byte[] export(Long projectId, LocalDateTime startTime, LocalDateTime endTime, String format,
                         Integer sourceType, Integer alarmLevel, Integer status, Long operatorId) {
        validatePositive("projectId", projectId);
        validateTimeRangeRequired(startTime, endTime);
        String f = StringUtils.hasText(format) ? format.toUpperCase() : "XLSX";
        if (!List.of("CSV", "XLSX").contains(f)) throw new AlarmException("format 参数不合法");
        validateDimension("告警来源", sourceType, 1, 3);
        validateDimension("告警级别", alarmLevel, 1, 3);
        validateDimension("处置状态", status, 0, 2);
        validatePositive("operatorId", operatorId);
        List<AlarmListVO> rows = alarmRecordMapper.selectForExport(projectId, startTime, endTime, sourceType,
                alarmLevel, status, operatorId);
        String csv = toCsv(rows);
        return "CSV".equals(f) ? csv.getBytes(StandardCharsets.UTF_8) : xlsx(rows);
    }

    public PageResult<AlarmPushLogVO> pushLogs(Long id, Integer pushStatus, long pageNum, long pageSize) {
        requireDetail(id);
        validatePage(pageNum, pageSize, 100);
        validateDimension("推送状态", pushStatus, 0, 2);
        long total = pushLogMapper.countPage(id, pushStatus);
        return new PageResult<>(total, pages(total, pageSize), pageNum, pageSize,
                pushLogMapper.selectPage(id, pushStatus, (pageNum - 1) * pageSize, pageSize));
    }

    @Transactional(rollbackFor = Exception.class)
    public void retryPush(Long pushLogId) {
        requirePositiveId(pushLogId);
        if (pushLogMapper.resetFailedForRetry(pushLogId) != 1) throw new AlarmNotFoundException("失败的推送记录不存在");
    }

    public AiAlarmDetailVO aiDetail(Long id) {
        requirePositiveId(id);
        AiAlarmDetailVO detail = alarmRecordMapper.selectAiDetail(id);
        if (detail == null) throw new AlarmNotFoundException("AI告警不存在或已删除");
        detail.setScreenshotUrls(screenshotMapper.selectUrls(id));
        return detail;
    }

    /**
     * 关联 AI 告警截图：校验告警存在后写入 biz_ai_screenshot。
     * 重复请求幂等：同告警下已存在相同地址与类型的截图时直接返回已有记录主键。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addScreenshot(Long alarmId, AiScreenshotCreateDTO request) {
        AlarmRecord alarm = requireAlarm(alarmId);
        String url = StringUtils.hasText(request.getScreenshotUrl()) ? request.getScreenshotUrl().trim() : "";
        if (!StringUtils.hasText(url)) throw new AlarmException("截图地址不能为空");
        // screenshot_type 非空列，缺省按 1（原始截图）处理
        Integer type = request.getScreenshotType() == null ? 1 : request.getScreenshotType();
        Long existingId = screenshotMapper.selectIdByAlarmAndUrl(alarmId, url, type);
        if (existingId != null) return existingId;
        AiScreenshot screenshot = new AiScreenshot();
        screenshot.setAlarmId(alarmId);
        screenshot.setScreenshotUrl(url);
        screenshot.setScreenshotType(type);
        screenshot.setFileSize(request.getFileSize());
        screenshot.setWidth(request.getWidth());
        screenshot.setHeight(request.getHeight());
        if (screenshotMapper.insert(screenshot) != 1) throw new AlarmException("保存告警截图失败");
        // 同步写入视频监控记录表（recordType=3：AI 告警截图），供视频监控模块查询；失败不影响主记录
        try {
            BizVideoRecord record = new BizVideoRecord();
            record.setCameraId(alarm.getCameraId());
            record.setRecordType(3);
            record.setFilePath(url);
            record.setStartTime(alarm.getAlarmTime() != null ? alarm.getAlarmTime() : LocalDateTime.now());
            record.setFileSize(request.getFileSize());
            record.setExpireTime(LocalDateTime.now().plusDays(30));
            record.setTriggerSource("ai");
            record.setAiLabel(request.getAiLabel());
            record.setAiConfidence(request.getAiConfidence());
            record.setDeleted(0);
            record.setCreatedAt(LocalDateTime.now());
            videoRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("AI 截图同步写入视频监控记录失败: alarmId={}, url={}, err={}", alarmId, url, e.toString());
        }
        return screenshot.getId();
    }

    /**
     * 删除指定 AI 告警关联的全部截图：删除数据库记录并清理本地图片文件，返回删除条数。
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteAiScreenshots(Long alarmId) {
        requirePositiveId(alarmId);
        List<AiScreenshot> screenshots = screenshotMapper.selectByAlarmId(alarmId);
        if (screenshots.isEmpty()) return 0;
        int deleted = screenshotMapper.deleteByAlarmId(alarmId);
        screenshots.forEach(s -> deleteLocalFile(s.getScreenshotUrl()));
        return deleted;
    }

    /**
     * 按主键删除单条 AI 截图：删除数据库记录并清理本地图片文件。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAiScreenshot(Long id) {
        if (id == null || id < 1) throw new AlarmException("截图ID必须为正整数");
        AiScreenshot screenshot = screenshotMapper.selectById(id);
        if (screenshot == null) throw new AlarmNotFoundException("AI截图不存在");
        if (screenshotMapper.deleteById(id) != 1) throw new AlarmException("AI截图删除失败");
        deleteLocalFile(screenshot.getScreenshotUrl());
    }

    /** 解析截图 URL 定位本地文件并删除；仅处理 /ai_image/** 下的文件，失败不影响数据库删除。 */
    private void deleteLocalFile(String url) {
        if (!StringUtils.hasText(url)) return;
        try {
            String path = url;
            if (url.startsWith("http://") || url.startsWith("https://")) {
                path = new URI(url).getPath();
            }
            if (path == null || !path.startsWith("/ai_image/")) return;
            String root = StringUtils.hasText(aiImageDir)
                    ? aiImageDir
                    : System.getProperty("user.dir") + File.separator + "ai_image";
            Path file = Paths.get(root).resolve(path.substring("/ai_image/".length()));
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // 文件删除失败仅遗留本地文件，不影响数据库记录删除
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void dispose(AlarmDisposalDTO request, Long operatorId, String operatorName) {
        if (request == null || request.getAlarmId() == null) throw new AlarmException("告警ID不能为空");
        if (!StringUtils.hasText(request.getDisposalContent())) throw new AlarmException("处置内容不能为空");
        Integer conclusion = request.getDisposalConclusion();
        if (conclusion == null || conclusion < 1 || conclusion > 3) throw new AlarmException("处置结论必须为1、2或3");
        if (conclusion == 2 && !StringUtils.hasText(request.getFalseReason())) throw new AlarmException("误报必须填写误报原因");
        AlarmRecord alarm = requireAlarm(request.getAlarmId());
        AlarmDisposal disposal = new AlarmDisposal();
        disposal.setAlarmId(alarm.getId());
        disposal.setOperatorId(operatorId == null ? 0L : operatorId);
        disposal.setOperatorName(StringUtils.hasText(operatorName) ? operatorName.trim() : "系统用户");
        disposal.setDisposalContent(request.getDisposalContent().trim());
        disposal.setDisposalConclusion(conclusion);
        disposal.setFalseReason(StringUtils.hasText(request.getFalseReason()) ? request.getFalseReason().trim() : null);
        disposal.setDisposalTime(LocalDateTime.now());
        if (disposalMapper.insert(disposal) != 1) throw new AlarmException("保存告警处置记录失败");
        if (alarmRecordMapper.updateStatus(alarm.getId(), 2) != 1) throw new AlarmException("更新告警状态失败");
        rebuildDailyStatistics(alarm.getProjectId(), alarm.getAlarmTime() == null ? LocalDate.now() : alarm.getAlarmTime().toLocalDate());
    }

    public AlarmHistoryVO history(Long id) {
        AlarmRecord alarm = requireAlarm(id);
        AlarmHistoryVO history = new AlarmHistoryVO();
        history.setDisposalRecords(disposalMapper.selectHistory(alarm.getId()));
        history.setRecoveryRecord(recoveryMapper.selectHistory(alarm.getId()));
        return history;
    }

    public List<DailyAlarmStatVO> dailyStatistics(Long projectId, LocalDate date) {
        validatePositive("projectId", projectId);
        if (date == null) throw new AlarmException("date 不能为空");
        return dailyStatMapper.selectByProjectAndDate(projectId, date);
    }

    private AlarmListVO requireDetail(Long id) {
        requirePositiveId(id);
        AlarmListVO result = alarmRecordMapper.selectDetail(id);
        if (result == null) throw new AlarmNotFoundException("告警不存在或已删除");
        return result;
    }

    private AlarmRecord requireAlarm(Long id) {
        requirePositiveId(id);
        AlarmRecord alarm = alarmRecordMapper.selectById(id);
        if (alarm == null || !Integer.valueOf(0).equals(alarm.getIsDeleted())) throw new AlarmNotFoundException("告警不存在或已删除");
        return alarm;
    }

    private void rebuildDailyStatistics(Long projectId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        dailyStatMapper.deleteByProjectAndDate(projectId, date);
        dailyStatMapper.insertAggregate(projectId, start, start.plusDays(1));
    }

    private List<AlarmDimensionCountVO> dimensions(List<AlarmCountVO> source, Map<Integer, String> labels,
                                                            String queryKey) {
        long total = source.stream().mapToLong(AlarmCountVO::getCount).sum();
        return source.stream().map(v -> {
            AlarmDimensionCountVO item = new AlarmDimensionCountVO();
            item.setDimension(String.valueOf(v.getDimension()));
            item.setLabel(labels.getOrDefault(v.getDimension(), String.valueOf(v.getDimension())));
            item.setCount(v.getCount());
            item.setPercentage(percent(v.getCount(), total));
            item.setQuery(Map.of(queryKey, String.valueOf(v.getDimension())));
            return item;
        }).toList();
    }

    private String toCsv(List<AlarmListVO> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF告警编号,项目ID,来源,级别,类型,内容,状态,设备ID,监测点ID,摄像头ID,告警时间,恢复时间\n");
        for (AlarmListVO v : rows) {
            sb.append(csv(v.getAlarmNo())).append(',').append(csv(v.getProjectId())).append(',')
                    .append(csv(v.getSourceTypeText())).append(',').append(csv(v.getAlarmLevelText())).append(',')
                    .append(csv(v.getAlarmTypeCode())).append(',').append(csv(v.getAlarmContent())).append(',')
                    .append(csv(v.getStatusText())).append(',').append(csv(v.getDeviceId())).append(',')
                    .append(csv(v.getMonitorPointId())).append(',').append(csv(v.getCameraId())).append(',')
                    .append(csv(v.getAlarmTime())).append(',').append(csv(v.getRecoveryTime())).append('\n');
        }
        return sb.toString();
    }

    private byte[] xlsx(List<AlarmListVO> rows) {
        List<List<Object>> values = new java.util.ArrayList<>();
        values.add(List.of("告警编号", "项目ID", "来源", "级别", "类型", "内容", "状态", "设备ID", "监测点ID", "摄像头ID", "告警时间", "恢复时间"));
        for (AlarmListVO row : rows) {
            values.add(java.util.Arrays.asList(row.getAlarmNo(), row.getProjectId(), row.getSourceTypeText(),
                    row.getAlarmLevelText(), row.getAlarmTypeCode(), row.getAlarmContent(), row.getStatusText(),
                    row.getDeviceId(), row.getMonitorPointId(), row.getCameraId(), row.getAlarmTime(),
                    row.getRecoveryTime()));
        }
        try {
            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            for (int i = 0; i < values.size(); i++) {
                xml.append("<row r=\"").append(i + 1).append("\">");
                for (int j = 0; j < values.get(i).size(); j++) {
                    Object value = values.get(i).get(j);
                    xml.append("<c r=\"").append((char) ('A' + j)).append(i + 1)
                            .append("\" t=\"inlineStr\"><is><t>")
                            .append(xml(value == null ? "" : value.toString())).append("</t></is></c>");
                }
                xml.append("</row>");
            }
            xml.append("</sheetData></worksheet>");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
                put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
                put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"告警统计\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
                put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
                put(zip, "xl/worksheets/sheet1.xml", xml.toString());
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new AlarmException("导出告警统计失败");
        }
    }
    private static void put(ZipOutputStream zip, String name, String data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String s = value.toString().replace("\"", "\"\"");
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s + "\"" : s;
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static void validatePositive(String name, Long value) {
        if (value != null && value < 1) throw new AlarmException(name + " 必须为正整数");
    }

    private static void validatePage(long pageNum, long pageSize, long max) {
        if (pageNum < 1 || pageSize < 1 || pageSize > max) throw new AlarmException("分页参数不合法");
    }

    private static void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && end.isBefore(start)) throw new AlarmException("结束时间不能早于开始时间");
    }

    private static void validateTimeRangeRequired(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) throw new AlarmException("时间范围不合法");
    }

    private static void validateDimension(String name, Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) throw new AlarmException(name + "参数不合法");
    }

    private static void requirePositiveId(Long id) {
        if (id == null || id < 1) throw new AlarmException("告警ID必须为正整数");
    }

    private static BigDecimal percent(long value, long total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    private static int value(Integer v) {
        return v == null ? 0 : v;
    }

    private static long pages(long total, long size) {
        return total == 0 ? 0 : (total + size - 1) / size;
    }
}